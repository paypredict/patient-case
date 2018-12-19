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
import java.util.logging.Level
import java.util.logging.Logger
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.system.exitProcess

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/22/2018.
 */
class RequisitionFormsPdfProcessing(
    private val options: Options,
    private val pdfFile: File
) {
    private val log: Logger by lazy {
        Logger.getLogger(RequisitionFormsPdfProcessing::class.qualifiedName)
    }

    data class Options(
        val requisitionFormsDir: File,
        val requisitionPDFsDir: File,
        val minFileTime: Long?,
        val nThreads: Int?
    )

    private var pageIndex: Int = 0

    private fun process(
        barcodeReader: () -> BarcodeReader = { initBarcodeReader() }
    ): Boolean {
        val pdfFileId = pdfFile.toDigest().toHexString()

        val requisitionPDFs = DBS.Collections.requisitionPDFs()

        val isProcessed =
            requisitionPDFs
                .findById(pdfFileId)
                ?.getBoolean("isProcessed", false)
                ?: false
        if (isProcessed) return true

        requisitionPDFs
            .upsertOne(pdfFileId._id(), "name" to pdfFile.name, "time" to pdfFile.lastModified())

        val barcodeFoundSet = mutableSetOf<String>()

        val requisitionForms = DBS.Collections.requisitionForms()
        val requisitionFormImageProcessor = object : PDFImageProcessor() {
            val reader = barcodeReader()

            override fun onImage(image: BufferedImage) {
                if (image.width < 200 || image.height < 200)
                    return
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
                        log.info("barcode $barcode found in $pdfFile")
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
                        log.log(Level.WARNING, "PDF $pdfFile: image processing error on $formId", e)
                    }
                }

            override fun close() {
                service.shutdown()
                service.awaitTermination(20, TimeUnit.SECONDS)
            }
        }

        return try {
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
            } else {
                log.info("no barcode found in $pdfFile")
            }
            isRestartRequired =
                    Runtime.getRuntime().let {
                        val used = it.totalMemory() - it.freeMemory()
                        used > 5L * 1024 * 1024 * 1024
                    }
            true
        } catch (e: Throwable) {
            requisitionPDFs.upsertOne(pdfFileId._id(), "error" to e.toDocument())
            log.log(Level.WARNING, "error on processing $pdfFile", e)
            isRestartRequired = true
            false
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

        private fun Array<String>.toOptions(
            clientDefault: String = "test",
            daysDefault: String = "",
            threadsDefault: String = ""
        ): Options {
            val client = option("client", clientDefault)
            val clientDir = File("/PayPredict/clients").resolve(client)
            val days = option("days", daysDefault).toIntOrNull()
            val threads = option("threads", threadsDefault).toIntOrNull()

            return Options(
                requisitionFormsDir = clientDir.resolve("requisitionForms"),
                requisitionPDFsDir = clientDir.resolve("requisitionPDFs"),
                minFileTime = days?.let { System.currentTimeMillis() - it * 24 * 60 * 60 * 1000 },
                nThreads = threads
            )
        }

        private fun initBarcodeReader(): BarcodeReader =
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
            val options = args.toOptions(threadsDefault = "8")
            val service: ExecutorService = Executors.newFixedThreadPool(options.nThreads!!)
            val count = AtomicInteger()
            for (file in options.requisitionPDFsDir.walk().sortedByDescending { it.lastModified() }) {
                if (!file.isFile) continue
                if (!file.name.endsWith(".pdf", ignoreCase = true)) continue
                if (options.minFileTime != null && file.lastModified() < options.minFileTime) continue
                count.incrementAndGet()
                service.submit {
                    try {
                        println("processing $file")
                        RequisitionFormsPdfProcessing(options, file).process()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    } finally {
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

    private var isRestartRequired: Boolean = false

    object Auto {
        private val LOG: Logger = Logger.getLogger(Auto::class.qualifiedName)

        @JvmStatic
        fun main(args: Array<String>) {
            LOG.info("Starting " + args.toList())
            val options = args.toOptions(daysDefault = "7")
            val barcodeReader = initBarcodeReader()
            for (file in options.requisitionPDFsDir.walk().sortedByDescending { it.lastModified() }) {
                if (!file.isFile) continue
                if (!file.name.endsWith(".pdf", ignoreCase = true)) continue
                if (options.minFileTime != null && file.lastModified() < options.minFileTime) continue
                try {
                    LOG.info("processing $file")
                    with(RequisitionFormsPdfProcessing(options, file)) {
                        process { barcodeReader }
                        if (isRestartRequired) {
                            LOG.info("Restarting")
                            exitProcess(302)
                        }
                    }
                } catch (e: Throwable) {
                    LOG.log(Level.SEVERE, "unhandled error", e)
                }
            }
            LOG.info("DONE")
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
