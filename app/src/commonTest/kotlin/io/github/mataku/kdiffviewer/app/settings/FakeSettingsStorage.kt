package io.github.mataku.kdiffviewer.app.settings

class FakeSettingsStorage(
  var content: String? = null,
  var failOnWrite: Boolean = false,
  var failOnRead: Boolean = false,
) : SettingsStorage {
  val writes = mutableListOf<String>()
  val events = mutableListOf<String>()
  var backupCalls = 0
    private set

  override fun read(): String? {
    if (failOnRead) {
      throw IllegalStateException("read failed")
    }
    return content
  }

  override fun write(text: String) {
    if (failOnWrite) {
      throw IllegalStateException("write failed")
    }
    events += "write"
    writes += text
    content = text
  }

  override fun backup() {
    events += "backup"
    backupCalls++
  }
}
