package com.kaayakasetu.contact;

/** A raw row of the contacts table. */
public record Contact(
        long id,
        String senderType,
        long senderId,
        String receiverType,
        long receiverId,
        Long jobId,
        String message,
        String status,
        int phoneShared,
        String createdAt) {}
