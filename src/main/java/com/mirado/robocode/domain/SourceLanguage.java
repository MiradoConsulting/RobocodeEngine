package com.mirado.robocode.domain;

/**
 * Created by oskarkjellin on 2016-12-16.
 */
public enum SourceLanguage
{
    JAVA(".java"),
    CLOJURE(".clj");
    private final String extension;

    SourceLanguage(String extension)
    {
        this.extension = extension;
    }

    public String getExtension()
    {
        return extension;
    }
}
