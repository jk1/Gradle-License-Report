package com.github.jk1.license.util


class Files {

    static String getExtension(String fullName) {
        String fileName = new File(fullName).getName()
        int dotIndex = fileName.lastIndexOf('.')
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1)
    }
}
