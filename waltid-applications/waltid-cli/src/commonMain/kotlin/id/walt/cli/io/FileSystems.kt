package id.walt.cli.io

expect class FileSystems {
    companion object {
        fun getDefault(): FileSystem
    }
}