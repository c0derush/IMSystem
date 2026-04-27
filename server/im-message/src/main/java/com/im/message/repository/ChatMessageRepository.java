package com.im.message.repository;

import com.im.message.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySenderIdAndReceiverIdOrReceiverIdAndSenderIdOrderBySentAtDesc(
            Long senderId1, Long receiverId1, Long senderId2, Long receiverId2, Pageable pageable);

    long countByReceiverIdAndReadFalse(Long receiverId);

    long countByReceiverIdAndSenderIdAndReadFalse(Long receiverId, Long senderId);

    ChatMessage findByClientMessageId(String clientMessageId);

    List<ChatMessage> findByGroupIdOrderBySentAtDesc(Long groupId, Pageable pageable);
}
