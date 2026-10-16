package cash.p.terminal.modules.manageaccount.publickeys

import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysModule.ZcashViewKey
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysModule.ZcashViewKeyKind
import org.junit.Test
import kotlin.test.assertEquals

class ZcashViewKeyTest {

    @Test
    fun kind_unifiedFullViewingKey_isUnified() {
        assertEquals(ZcashViewKeyKind.Unified, ZcashViewKey("uview1abcdef").kind)
    }

    @Test
    fun kind_saplingViewingKey_isSapling() {
        assertEquals(ZcashViewKeyKind.Sapling, ZcashViewKey("zxviews1abcdef").kind)
    }

    @Test
    fun kind_transparentOnlyAccountExportsBareXpub_isTransparent() {
        assertEquals(ZcashViewKeyKind.Transparent, ZcashViewKey("xpub6C1Abcdef").kind)
    }
}
