package id.walt.ebsi

actual object Delay {
  actual fun delay(timeMillis: Long) {
    Thread.sleep(timeMillis)
  }
}
