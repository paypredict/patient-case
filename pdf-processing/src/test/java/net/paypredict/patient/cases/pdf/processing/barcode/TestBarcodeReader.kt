package net.paypredict.patient.cases.pdf.processing.barcode

import com.dynamsoft.barcode.jni.BarcodeReader
import com.dynamsoft.barcode.jni.EnumImagePixelFormat
import com.dynamsoft.barcode.jni.TextResult
import sun.awt.image.ByteComponentRaster
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 *
 *
 * Created by alexei.vylegzhanin@gmail.com on 9/22/2018.
 */
object TestBarcodeReaderDir {
    @JvmStatic
    fun main(args: Array<String>) {
        val reader = barcodeReader()
        val barCodeNotFoundFiles = mutableListOf<File>()
        File(args.last())
            .listFiles { file: File -> file.isFile && file.name.endsWith(".png", ignoreCase = true) }
            ?.forEach { file ->
                print(file.name + ": ")
                val results: Array<out TextResult> = reader
                    .decodeFileInMemory(file.readBytes(), "exampleTpl")
                if (results.isEmpty()) {
                    println("WARNING: barcode not found")
                    barCodeNotFoundFiles += file
                }
                println(results.joinToString { it.barcodeText })
            }

        if (barCodeNotFoundFiles.isNotEmpty()) {
            println("barcode not found in ${barCodeNotFoundFiles.size} files: " +
                    barCodeNotFoundFiles.joinToString { it.name })
        }
    }
}

object TestBarcodeReaderParams {
    @JvmStatic
    fun main(args: Array<String>) {
        val reader = barcodeReader()
        val file = File(args.first())
        val results = reader.decodeFileInMemory(file.readBytes(), "exampleTpl")
        for (result in results) {
            println(result.barcodeText)
        }
    }
}

object TestBarcodeReaderGrayScaled {
    @JvmStatic
    fun main(args: Array<String>) {
        val reader = barcodeReader()
        val file = File(args.first())
        val image0 = ImageIO.read(file)
        val image = BufferedImage(image0.width, image0.height, BufferedImage.TYPE_BYTE_GRAY)
        val graphics = image.createGraphics()
        graphics.drawImage(image0, 0, 0, null)
        graphics.dispose()

        val bytes = (image.raster as ByteComponentRaster).dataStorage
        val results =
            reader.decodeBuffer(
                bytes,
                image.width,
                image.height,
                image.width,
                EnumImagePixelFormat.IPF_GrayScaled,
                "exampleTpl"
            )
        for (result in results) {
            println(result.barcodeText)
        }
    }
}

private fun barcodeReader(): BarcodeReader =
    BarcodeReader(
        "t0068NQAAAJ6+yW41SfwL2XeoHbPcCGbmPuGvaekIrdNUE5n8OXUbcF6gGQzpTrawX88fJ8VUlpuTSWKg3IRNUKElU9HnBYs="
    ).apply {
        //language=JSON
        appendParameterTemplate(
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
