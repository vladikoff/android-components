package mozilla.components.service.sync.logins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import org.mozilla.sync15.logins.SyncResult

@RunWith(RobolectricTestRunner::class)
class SyncLoginsTest {

    @Test
    fun syncLoginsTest() {
        SyncResult.fromValue(42)
    }
}
