package connectors.commons

import connectors.Connector
import mu.KLogger


data class RowError(
    val origin: Connector,
    val message: String,
    val exception: Exception
) {
    fun log(logger: KLogger) {
        logger.error(this.message, this.exception)
    }
}
