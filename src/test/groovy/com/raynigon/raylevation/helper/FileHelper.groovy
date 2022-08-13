package com.raynigon.raylevation.helper

class FileHelper {

    static createTemporaryDirectory() {
        File dir = File.createTempDir()
        dir.deleteOnExit()
        return dir.toPath()
    }
}
