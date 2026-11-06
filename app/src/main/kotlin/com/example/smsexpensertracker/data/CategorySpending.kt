
/**
 * Simple DTO for aggregation of spending per category within a given period.
 */
data class CategorySpending(
    val categoryId: Long,
    val total: Long
)
