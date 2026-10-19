package com.kaayakasetu.contact;

/** A contact row plus the names/phone numbers of both sides (looked up from workers / jobs). */
public record ContactResponse(
        long id,
        String senderType,
        long senderId,
        String receiverType,
        long receiverId,
        Long jobId,
        String message,
        String status,
        int phoneShared,
        String createdAt,
        String senderName,
        String senderPhone,
        String receiverName,
        String receiverPhone) {}
