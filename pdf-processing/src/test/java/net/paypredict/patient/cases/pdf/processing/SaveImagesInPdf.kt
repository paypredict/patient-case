package net.paypredict.patient.cases.pdf.processing

import org.apache.pdfbox.contentstream.PDFStreamEngine
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import javax.imageio.ImageIO
import java.io.File

class SaveImagesInPdf(val outDir: File, val name: String) : PDFStreamEngine() {
    var imageNumber = 1
    override fun processOperator(operator: Operator, operands: List<COSBase>) {
        if ("Do" == operator.name) {
            val objectName = operands[0] as COSName
            val xObject = resources.getXObject(objectName)
            when (xObject) {
                is PDImageXObject -> {
                    ImageIO.write(
                        xObject.image,
                        "PNG",
                        outDir.resolve("$name.image_$imageNumber.png")
                    )
                    println("Image saved.")
                    imageNumber++

                }
                is PDFormXObject -> showForm(xObject)
            }
        } else {
            super.processOperator(operator, operands)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val fileName = args.first()
            var document: PDDocument? = null
            try {
                val file = File(fileName)
                document = PDDocument.load(file)
                val printer = SaveImagesInPdf(file.parentFile, file.name)
                var pageNum = 0
                for (page in document.pages) {
                    pageNum++
                    println("Processing page: $pageNum")
                    printer.processPage(page)
                }
            } finally {
                document?.close()
            }
        }
    }

}