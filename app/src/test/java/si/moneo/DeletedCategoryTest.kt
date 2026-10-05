package si.moneo

import org.junit.Assert.assertEquals
import org.junit.Test
import si.moneo.data.db.entity.CategoryEntity
import si.moneo.data.db.entity.TransactionSource
import si.moneo.data.db.entity.TransactionType
import si.moneo.ui.TransactionUi
import si.moneo.ui.UNCATEGORIZED
import si.moneo.ui.categorySpends
import java.time.LocalDate

class DeletedCategoryTest {

    private fun tx(cents: Long, cat: String?) = TransactionUi(
        uid = "$cents-$cat", type = TransactionType.EXPENSE, amountCents = cents, date = LocalDate.of(2026, 10, 1),
        comment = "", categoryUid = cat, categoryTitle = null, categoryColor = null, accountUid = null, accountTitle = null,
        source = TransactionSource.MANUAL, confirmed = true,
    )

    @Test fun deletedCategoryMergesWithUncategorized() {
        val hrana = CategoryEntity(uid = "h", title = "Hrana", type = TransactionType.EXPENSE)
        // "x" je izbrisana kategorija (ni med aktivnimi)
        val spends = categorySpends(listOf(tx(1000, "h"), tx(500, null), tx(300, "x")), listOf(hrana))
        assertEquals(2, spends.size)
        val none = spends.single { it.category === UNCATEGORIZED }
        assertEquals(800L, none.totalCents)
        assertEquals(2, none.count)
    }
}
