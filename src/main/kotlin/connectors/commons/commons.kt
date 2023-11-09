package connectors.commons

import connectors.Connector
import mu.KLogger
import org.apache.commons.csv.CSVRecord


data class RowError(
    val origin: Connector,
    val message: String,
    val exception: Exception
) {
    fun log(logger: KLogger) {
        logger.error(this.message, this.exception)
    }
}

object ItemManager {
    fun getValue(record: Any, fieldName: String) : Any? {
        return when (record) {
            is CSVRecord -> record[fieldName]
            else -> null
        }
    }
}
