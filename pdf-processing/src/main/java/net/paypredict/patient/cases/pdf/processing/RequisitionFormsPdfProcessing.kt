package net.paypredict.patient.cases.pdf.processing

import com.dynamsoft.barcode.jni.BarcodeReader
import com.dynamsoft.barcode.jni.EnumImagePixelFormat
import net.paypredict.patient.cases.mongo.*
import net.paypredict.patient.cases.toDigest
import net.paypredict.patient.cases.toHexString
import org.apache.pdfbox.contentstream.PDFStreamEngine
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.PDXObject
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.bson.Document
import sun.awt.image.ByteComponentRaster
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/22/2018.
 */
class RequisitionFormsPdfProcessing(
    private val options: Options,
    private val pdfFile: File
) {
    data class Options(
        val requisitionFormsDir: File,
        val requisitionPDFsDir: File
    )

    private var pageIndex: Int = 0

    private fun process() {
        val pdfFileId = pdfFile.toDigest().toHexString()

        val barcodeFoundSet = mutableSetOf<String>()

        val requisitionForms = DBS.Collections.requisitionForms()
        val requisitionFormImageProcessor = object : PDFImageProcessor() {
            val reader = barcodeReader()

            override fun onImage(image: BufferedImage) {
                val grayBytes: ByteArray = image.toGrayBytes()
                val formId = grayBytes.toDigest().toHexString()
                if (requisitionForms.findById(formId)?.opt<Boolean>("isProcessed") == true)
                    return

                val imageWriting = writeImage(image, formId)

                requisitionForms.upsertOne(formId._id(), "pdf" to doc {
                    self["id"] = pdfFileId
                    self["page"] = pageIndex
                    self["name"] = pdfFile.name
                })

                try {
                    val results =
                        reader.decodeBuffer(
                            grayBytes,
                            image.width,
                            image.height,
                            image.width,
                            EnumImagePixelFormat.IPF_GrayScaled,
                            "exampleTpl"
                        )
                    val barcode = results.firstOrNull()?.barcodeText
                    if (barcode != null) {
                        requisitionForms.upsertOne(formId._id(), "barcode" to barcode)
                        barcodeFoundSet += barcode
                    } else {
                        requisitionForms.upsertOne(formId._id(), "barcodeNotFound" to true)
                    }
                } catch (e: Throwable) {
                    requisitionForms.upsertOne(formId._id(), "error" to e.toDocument())
                } finally {
                    imageWriting.get()
                    requisitionForms.upsertOne(formId._id(), "isProcessed" to true)
                }

            }

            val service: ExecutorService = Executors.newSingleThreadExecutor()

            private fun writeImage(image: BufferedImage, formId: String): Future<*> =
                service.submit {
                    try {
                        val dir = options.requisitionFormsDir.apply { mkdir() }
                            .resolve(formId.substring(0, 4)).apply { mkdir() }
                            .resolve(formId).apply { mkdir() }

                        fun write(file: File, image: BufferedImage, format: String) {
                            val tmp = File.createTempFile("${file.name}.", ".tmp", dir)
                            ImageIO.write(image, format, tmp)
                            tmp.renameTo(file)
                        }

                        write(dir.resolve("requisition.jpg"), image, "JPG")

                        val scale = min(
                            64.toDouble() / image.height.toDouble(),
                            64.toDouble() / image.width.toDouble()
                        )
                        write(
                            file = dir.resolve("thumbnail.jpg"),
                            image = BufferedImage(
                                (image.width * scale).toInt(),
                                (image.height * scale).toInt(),
                                BufferedImage.TYPE_INT_BGR
                            ).also { thumbnail ->
                                val graphics = thumbnail.createGraphics()
                                graphics.drawImage(
                                    image.getScaledInstance(
                                        thumbnail.width,
                                        thumbnail.height,
                                        Image.SCALE_SMOOTH
                                    ),
                                    0,
                                    0,
                                    null
                                )
                                graphics.dispose()
                            },
                            format = "JPG"
                        )
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

            override fun close() {
                service.shutdown()
                service.awaitTermination(20, TimeUnit.SECONDS)
            }
        }

        val requisitionPDFs = DBS.Collections.requisitionPDFs()
        if (requisitionPDFs.findById(pdfFileId)?.opt<Boolean>("isProcessed") == true) return
        requisitionPDFs.upsertOne(
            pdfFileId._id(),
            "name" to pdfFile.name,
            "time" to pdfFile.lastModified()
        )

        try {
            PDDocument.load(pdfFile).use { pdDocument ->
                pdDocument.pages.forEachIndexed { index: Int, page: PDPage ->
                    pageIndex = index
                    requisitionFormImageProcessor.processPage(page)
                }
            }
            if (barcodeFoundSet.isNotEmpty()) {
                val barcodeRange = doc {
                    self["min"] = barcodeFoundSet.min()
                    self["max"] = barcodeFoundSet.max()
                }
                requisitionPDFs.upsertOne(pdfFileId._id(), "barcodeRange" to barcodeRange)
            }
        } catch (e: Throwable) {
            requisitionPDFs.upsertOne(pdfFileId._id(), "error" to e.toDocument())
        } finally {
            requisitionPDFs.upsertOne(pdfFileId._id(), "isProcessed" to true)
            requisitionFormImageProcessor.close()
        }

    }

    private fun BufferedImage.toGrayBytes(): ByteArray {
        val gray = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val graphics = gray.createGraphics()
        graphics.drawImage(this, 0, 0, null)
        graphics.dispose()
        return (gray.raster as ByteComponentRaster).dataStorage
    }

    private abstract class PDFImageProcessor : PDFStreamEngine() {
        abstract fun onImage(image: BufferedImage)

        override fun processOperator(operator: Operator, operands: List<COSBase>) {
            when (operator.name) {
                "Do" -> {
                    val objectName: COSName = operands[0] as COSName
                    val xObject: PDXObject? = resources.getXObject(objectName)
                    when (xObject) {
                        is PDImageXObject -> onImage(xObject.image)
                        is PDFormXObject -> showForm(xObject)
                    }
                }
                else -> super.processOperator(operator, operands)
            }
        }

        abstract fun close()
    }

    companion object {
        private fun Array<String>.option(name: String, default: String): String {
            val prefix = "--$name:"
            return firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix) ?: default
        }

        private fun Array<String>.toOptions(): Options {
            val client = option("client", "test")
            val clientDir = File("/PayPredict/clients").resolve(client)
            val requisitionFormsDir: File = clientDir.resolve("requisitionForms")
            val requisitionPDFsDir: File = clientDir.resolve("requisitionPDFs")
            return Options(
                requisitionFormsDir = requisitionFormsDir,
                requisitionPDFsDir = requisitionPDFsDir
            )
        }

        private fun barcodeReader(): BarcodeReader =
            BarcodeReader(Conf.license)
                .apply { Conf.templates.forEach { appendParameterTemplate(it) } }

        @JvmStatic
        fun main(args: Array<String>) {
            RequisitionFormsPdfProcessing(args.toOptions(), File(args.last())).process()
        }
    }

    object Dir {
        @JvmStatic
        fun main(args: Array<String>) {
            val service: ExecutorService = Executors.newFixedThreadPool(8)
            val options = args.toOptions()
            val count = AtomicInteger()
            for (file in options.requisitionPDFsDir.walk().sortedByDescending { it.lastModified() }) {
                if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                    count.incrementAndGet()
                    service.submit {
                        println("processing $file")
                        RequisitionFormsPdfProcessing(options, file).process()
                        count.decrementAndGet()
                    }
                }
            }
            service.shutdown()
            while (count.get() > 0) {
                println("$count files left")
                service.awaitTermination(10, TimeUnit.SECONDS)
            }
        }
    }

    object ClearCache {
        @JvmStatic
        fun main(args: Array<String>) {
            val isDateMatches = args.toDateFilter()
            val options = args.toOptions()
            val files = DBS.Collections.requisitionPDFs()
            val forms = DBS.Collections.requisitionForms()
            for (file in options.requisitionPDFsDir.walk()) {
                if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true) && file.isDateMatches()) {
                    println(file)
                    files.deleteMany(doc { self["name"] = file.name })
                    forms.deleteMany(doc { self["pdf.name"] = file.name })
                }
            }
        }

        private fun Array<String>.toDateFilter(): File.() -> Boolean {
            val afterPrefix = "--after:"
            val datePattern = "yyyy-MM-dd"
            val after = lastOrNull { it.startsWith(afterPrefix) }
                ?.let { SimpleDateFormat(datePattern).parse(it.removePrefix(afterPrefix)).time }
                ?: throw Exception("$afterPrefix$datePattern parameter required")
            return {
                lastModified() >= after
            }
        }
    }

    object ClearTrialLicenseMessagesInCache {
        @JvmStatic
        fun main(args: Array<String>) {
            val files = DBS.Collections.requisitionPDFs()
            val forms = DBS.Collections.requisitionForms()

            forms.find()
                .projection(doc {
                    self["barcode"] = 1
                    self["pdf.id"] = 1
                })
                .filter { (it["barcode"] as? String)?.endsWith("trial license.") == true }
                .mapNotNull { it.opt<String>("pdf", "id") }
                .toSet()
                .forEach {
                    println("delete $it")
                    files.deleteOne(doc { self["_id"] = it })
                    forms.deleteMany(doc { self["pdf.id"] = it })
                }
        }
    }

    private object Conf {
        private val file = File("/PayPredict/conf/com.dynamsoft.barcode.json")
        private val conf: Document
            get() = if (file.exists()) Document.parse(file.readText()) else Document()

        val license: String
            get() =
                conf.opt<String>("license") ?: throw Exception("$file: license not found")

        val templates: List<String>
            get() =
                conf.opt<List<*>>("templates")
                    ?.asSequence()
                    ?.filterIsInstance<Document>()
                    ?.map { it.toJson() }
                    ?.toList()
                    ?: defaultTemplates

        private val defaultTemplates: List<String> by lazy {
            //language=JSON
            listOf(
                """{
                      "ImageParameters": {
                        "Name": "exampleTpl",
                        "ScaleDownThreshold": 3400,
                        "BarcodeFormatIds": [
                          "DATAMATRIX"
                        ],
                        "ExpectedBarcodesCount": 512,
                        "DeblurLevel": 9,
                        "AntiDamageLevel": 9,
                        "TextFilterMode": "Enable"
                      }
                    }"""
            )
        }
    }
}
