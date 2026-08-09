package com.atrishub.atriscast.airplay

/** Minimal SPS parser for the geometry MediaCodec needs before it consumes the bitstream. */
internal object H264SpsParser {
    data class Dimensions(
        val codedWidth: Int,
        val codedHeight: Int,
        val visibleWidth: Int,
        val visibleHeight: Int,
    )

    fun parseDimensions(spsNal: ByteArray): Dimensions? = runCatching {
        require(spsNal.size >= 4) { "SPS is too short" }
        require((spsNal[0].toInt() and 0x1F) == 7) { "NAL is not an SPS" }

        val reader = BitReader(removeEmulationPreventionBytes(spsNal.copyOfRange(1, spsNal.size)))
        val profileIdc = reader.readBits(8)
        reader.readBits(8) // constraint flags + reserved_zero_2bits
        reader.readBits(8) // level_idc
        reader.readUnsignedExpGolomb() // seq_parameter_set_id

        var chromaFormatIdc = 1
        var separateColourPlaneFlag = false
        if (profileIdc in HIGH_PROFILE_IDS) {
            chromaFormatIdc = reader.readUnsignedExpGolomb()
            require(chromaFormatIdc in 0..3) { "invalid chroma_format_idc" }
            if (chromaFormatIdc == 3) separateColourPlaneFlag = reader.readBit()
            reader.readUnsignedExpGolomb() // bit_depth_luma_minus8
            reader.readUnsignedExpGolomb() // bit_depth_chroma_minus8
            reader.readBit() // qpprime_y_zero_transform_bypass_flag
            if (reader.readBit()) {
                val scalingListCount = if (chromaFormatIdc != 3) 8 else 12
                repeat(scalingListCount) { index ->
                    if (reader.readBit()) skipScalingList(reader, if (index < 6) 16 else 64)
                }
            }
        }

        reader.readUnsignedExpGolomb() // log2_max_frame_num_minus4
        when (reader.readUnsignedExpGolomb()) { // pic_order_cnt_type
            0 -> reader.readUnsignedExpGolomb() // log2_max_pic_order_cnt_lsb_minus4
            1 -> {
                reader.readBit() // delta_pic_order_always_zero_flag
                reader.readSignedExpGolomb() // offset_for_non_ref_pic
                reader.readSignedExpGolomb() // offset_for_top_to_bottom_field
                repeat(reader.readUnsignedExpGolomb()) { reader.readSignedExpGolomb() }
            }
        }

        reader.readUnsignedExpGolomb() // max_num_ref_frames
        reader.readBit() // gaps_in_frame_num_value_allowed_flag
        val picWidthInMbsMinus1 = reader.readUnsignedExpGolomb()
        val picHeightInMapUnitsMinus1 = reader.readUnsignedExpGolomb()
        val frameMbsOnlyFlag = reader.readBit()
        if (!frameMbsOnlyFlag) reader.readBit() // mb_adaptive_frame_field_flag
        reader.readBit() // direct_8x8_inference_flag

        var cropLeft = 0
        var cropRight = 0
        var cropTop = 0
        var cropBottom = 0
        if (reader.readBit()) {
            cropLeft = reader.readUnsignedExpGolomb()
            cropRight = reader.readUnsignedExpGolomb()
            cropTop = reader.readUnsignedExpGolomb()
            cropBottom = reader.readUnsignedExpGolomb()
        }

        val codedWidth = (picWidthInMbsMinus1 + 1) * 16
        val codedHeight = (2 - if (frameMbsOnlyFlag) 1 else 0) * (picHeightInMapUnitsMinus1 + 1) * 16

        val (subWidthC, subHeightC) = when {
            chromaFormatIdc == 0 || separateColourPlaneFlag -> 1 to 1
            chromaFormatIdc == 1 -> 2 to 2
            chromaFormatIdc == 2 -> 2 to 1
            else -> 1 to 1
        }
        val cropUnitX = if (chromaFormatIdc == 0 || separateColourPlaneFlag) 1 else subWidthC
        val cropUnitY = if (chromaFormatIdc == 0 || separateColourPlaneFlag) {
            2 - if (frameMbsOnlyFlag) 1 else 0
        } else {
            subHeightC * (2 - if (frameMbsOnlyFlag) 1 else 0)
        }

        val visibleWidth = codedWidth - (cropLeft + cropRight) * cropUnitX
        val visibleHeight = codedHeight - (cropTop + cropBottom) * cropUnitY
        require(codedWidth in 16..MAX_DIMENSION && codedHeight in 16..MAX_DIMENSION)
        require(visibleWidth in 1..codedWidth && visibleHeight in 1..codedHeight)

        Dimensions(codedWidth, codedHeight, visibleWidth, visibleHeight)
    }.getOrNull()

    private fun skipScalingList(reader: BitReader, size: Int) {
        var lastScale = 8
        var nextScale = 8
        repeat(size) {
            if (nextScale != 0) {
                val deltaScale = reader.readSignedExpGolomb()
                nextScale = (lastScale + deltaScale + 256) % 256
            }
            if (nextScale != 0) lastScale = nextScale
        }
    }

    private fun removeEmulationPreventionBytes(input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        var read = 0
        var write = 0
        var zeroCount = 0
        while (read < input.size) {
            val value = input[read].toInt() and 0xFF
            if (zeroCount >= 2 && value == 0x03) {
                read++
                zeroCount = 0
                continue
            }
            output[write++] = input[read++]
            zeroCount = if (value == 0) zeroCount + 1 else 0
        }
        return output.copyOf(write)
    }

    private class BitReader(private val data: ByteArray) {
        private var bitOffset = 0

        fun readBit(): Boolean = readBits(1) != 0

        fun readBits(count: Int): Int {
            require(count in 0..31)
            require(bitOffset + count <= data.size * 8) { "SPS ended unexpectedly" }
            var result = 0
            repeat(count) {
                val byte = data[bitOffset ushr 3].toInt() and 0xFF
                val bit = (byte ushr (7 - (bitOffset and 7))) and 1
                bitOffset++
                result = (result shl 1) or bit
            }
            return result
        }

        fun readUnsignedExpGolomb(): Int {
            var leadingZeroBits = 0
            while (!readBit()) {
                leadingZeroBits++
                require(leadingZeroBits <= 30) { "Exp-Golomb value is too large" }
            }
            if (leadingZeroBits == 0) return 0
            return (1 shl leadingZeroBits) - 1 + readBits(leadingZeroBits)
        }

        fun readSignedExpGolomb(): Int {
            val value = readUnsignedExpGolomb()
            return if ((value and 1) == 0) -(value / 2) else (value + 1) / 2
        }
    }

    private const val MAX_DIMENSION = 8192
    private val HIGH_PROFILE_IDS = setOf(44, 83, 86, 100, 110, 118, 122, 128, 134, 135, 138, 139, 244)
}
