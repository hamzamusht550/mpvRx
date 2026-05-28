package app.gyrolet.mpvrx.ui.player.ytdlp

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class YtdlpOptionsBuilderTest {
  @Test
  fun `buildFormat keeps VP9 profile 2, height, fps and HDR filters`() {
    val format = YtdlpOptionsBuilder.buildFormat(
      YtdlpOptionSettings(
        codecPreference = YtdlCodecPreference.VP9_PROFILE2,
        maxHeight = 2160,
        maxFps = 60,
        hdrPreference = YtdlHdrPreference.HDR,
      ),
    )

    assertContains(format, "vcodec^=?vp9.2")
    assertContains(format, "vcodec^=?vp09.02")
    assertContains(format, "[height<=?2160]")
    assertContains(format, "[fps<=?60]")
    assertContains(format, "[dynamic_range!=SDR]")
    assertContains(format, "/bv*+ba/b")
  }

  @Test
  fun `legacy H264 preference still maps to AVC selectors`() {
    val format = YtdlpOptionsBuilder.buildFormat(
      YtdlpOptionSettings(
        codecPreference = YtdlCodecPreference.AUTO,
        legacyPreferH264 = true,
      ),
    )

    assertContains(format, "vcodec^=?avc")
    assertContains(format, "vcodec^=?h264")
  }

  @Test
  fun `raw options split on commas and lines outside quotes`() {
    val options = YtdlpOptionsBuilder.parseRawOptions(
      """
      referer="https://example.test/a,b"
      --extractor-args="youtube:player_client=android,web"
      geo-bypass
      """.trimIndent(),
    )

    assertEquals(
      listOf(
        RawYtdlpOption("referer", "https://example.test/a,b"),
        RawYtdlpOption("extractor-args", "youtube:player_client=android,web"),
        RawYtdlpOption("geo-bypass", ""),
      ),
      options,
    )
  }

  @Test
  fun `raw option escaping quotes comma values`() {
    val resolved = YtdlpOptionsBuilder.build(
      YtdlpOptionSettings(
        subtitleLanguages = "en,ja",
        rawOptions = "referer=https://example.test/path,geo-bypass=",
      ),
    )

    assertContains(resolved.rawOptions, "sub-langs=\"en,ja\"")
    assertContains(resolved.rawOptions, "referer=https://example.test/path")
    assertContains(resolved.rawOptions, "geo-bypass=")
  }
}
