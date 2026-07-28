package com.local.mediaviewer.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

data class MediaFixtures(
    val png: File,
    val wav: File,
    val mp4: File,
)

class MediaFixtureFactory(
    private val directory: File,
) {
    fun create(): MediaFixtures {
        check(directory.mkdirs() || directory.isDirectory)
        val png = File(directory, "sample.png")
        val wav = File(directory, "sample.wav")
        val mp4 = File(directory, "sample.mp4")

        writePng(png)
        writeWav(wav)
        writeMp4(mp4)

        check(png.length() > 0L)
        check(wav.length() > WAV_HEADER_SIZE)
        check(mp4.length() > 0L)
        return MediaFixtures(png = png, wav = wav, mp4 = mp4)
    }

    private fun writePng(file: File) {
        val bitmap = Bitmap.createBitmap(
            320,
            180,
            Bitmap.Config.ARGB_8888,
        )
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(24, 32, 48))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(80, 180, 255)
            }
            canvas.drawRect(32f, 32f, 288f, 148f, paint)
            paint.color = Color.WHITE
            paint.textSize = 28f
            canvas.drawText("mediaviewer", 68f, 102f, paint)
            FileOutputStream(file).use { output ->
                check(
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output,
                    ),
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeWav(file: File) {
        val sampleRate = 8_000
        val durationSeconds = 4
        val sampleCount = sampleRate * durationSeconds
        val dataSize = sampleCount * 2
        DataOutputStream(FileOutputStream(file)).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianInt(sampleRate)
            output.writeLittleEndianInt(sampleRate * 2)
            output.writeLittleEndianShort(2)
            output.writeLittleEndianShort(16)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataSize)
            repeat(sampleCount) { index ->
                val angle =
                    2.0 * PI * 440.0 * index / sampleRate
                val value =
                    (sin(angle) * Short.MAX_VALUE * 0.25).toInt()
                output.writeLittleEndianShort(value)
            }
        }
    }

    private fun writeMp4(file: File) {
        val width = 160
        val height = 120
        val framesPerSecond = 10
        val frameCount = 40
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height,
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities
                    .COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 240_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(
            MediaFormat.MIMETYPE_VIDEO_AVC,
        )
        val muxer = MediaMuxer(
            file.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        var muxerStarted = false
        var trackIndex = -1

        codec.configure(
            format,
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
        codec.start()
        try {
            val info = MediaCodec.BufferInfo()
            var inputFrame = 0
            var inputEnded = false
            var outputEnded = false
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex =
                        codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        if (inputFrame < frameCount) {
                            val input = requireNotNull(
                                codec.getInputBuffer(inputIndex),
                            )
                            input.clear()
                            writeI420Frame(
                                buffer = input,
                                width = width,
                                height = height,
                                frame = inputFrame,
                            )
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                width * height * 3 / 2,
                                inputFrame * 1_000_000L /
                                    framesPerSecond,
                                0,
                            )
                            inputFrame += 1
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                frameCount * 1_000_000L /
                                    framesPerSecond,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        }
                    }
                }

                when (
                    val outputIndex =
                        codec.dequeueOutputBuffer(
                            info,
                            CODEC_TIMEOUT_US,
                        )
                ) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted)
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    else -> if (outputIndex >= 0) {
                        val output = requireNotNull(
                            codec.getOutputBuffer(outputIndex),
                        )
                        if (
                            info.flags and
                            MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        ) {
                            info.size = 0
                        }
                        if (info.size > 0) {
                            check(muxerStarted)
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(
                                trackIndex,
                                output,
                                info,
                            )
                        }
                        outputEnded =
                            info.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(
                            outputIndex,
                            false,
                        )
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
    }

    private fun writeI420Frame(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        frame: Int,
    ) {
        repeat(height) { y ->
            repeat(width) { x ->
                val luma = 32 + ((x + y + frame * 4) % 192)
                buffer.put(luma.toByte())
            }
        }
        val chromaSize = width * height / 4
        repeat(chromaSize) {
            buffer.put((96 + frame % 32).toByte())
        }
        repeat(chromaSize) {
            buffer.put((160 - frame % 32).toByte())
        }
    }

    private companion object {
        const val WAV_HEADER_SIZE = 44L
        const val CODEC_TIMEOUT_US = 10_000L
    }
}

private fun DataOutputStream.writeLittleEndianInt(value: Int) {
    writeByte(value and 0xff)
    writeByte(value ushr 8 and 0xff)
    writeByte(value ushr 16 and 0xff)
    writeByte(value ushr 24 and 0xff)
}

private fun DataOutputStream.writeLittleEndianShort(value: Int) {
    writeByte(value and 0xff)
    writeByte(value ushr 8 and 0xff)
}
