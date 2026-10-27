package de.nemeses.spring.bpmn.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

public interface ProcessDefinition {
    String name();
    Date createdDate();
    String bpmnXml();

    /**
     * @return lower case hex MD5 hash of the UTF-8 encoded {@link #bpmnXml()}
     */
    default String md5Hash() {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(bpmnXml().getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
}
