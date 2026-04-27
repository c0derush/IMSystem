package com.im.message.service;

import com.im.message.api.MessageService;
import com.im.message.api.dto.Message;
import com.im.message.entity.ChatMessage;
import com.im.message.repository.ChatMessageRepository;
import com.im.common.util.SnowflakeId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final SnowflakeId snowflakeId = new SnowflakeId(1, 1);

    public MessageServiceImpl(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    @Transactional
    public Message saveMessage(Message message) {
        String clientMessageId = message.clientMessageId();
        boolean hasClientId = clientMessageId != null && !clientMessageId.isBlank();
        if (hasClientId) {
            ChatMessage existing = chatMessageRepository.findByClientMessageId(clientMessageId);
            if (existing != null) {
                return toDto(existing);
            }
        }

        ChatMessage entity = new ChatMessage();
        long id = snowflakeId.nextId();
        entity.setId(id);
        entity.setSenderId(message.senderId());
        entity.setReceiverId(message.receiverId());
        entity.setGroupId(message.groupId());
        entity.setContent(message.content());
        if (hasClientId) {
            entity.setClientMessageId(clientMessageId);
        }
        entity.setSentAt(LocalDateTime.now());
        try {
            chatMessageRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // 并发插入同一 clientMessageId，返回已存在记录
            if (hasClientId) {
                ChatMessage existing = chatMessageRepository.findByClientMessageId(clientMessageId);
                if (existing != null) {
                    return toDto(existing);
                }
            }
            throw e;
        }
        return toDto(entity);
    }

    @Override
    public List<Message> queryHistory(Long userId, Long peerId, int page, int size) {
        var pageable = PageRequest.of(page, size);
        return chatMessageRepository
                .findBySenderIdAndReceiverIdOrReceiverIdAndSenderIdOrderBySentAtDesc(
                        userId, peerId, peerId, userId, pageable)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<Message> queryGroupHistory(Long groupId, int page, int size) {
        var pageable = PageRequest.of(page, size);
        return chatMessageRepository
                .findByGroupIdOrderBySentAtDesc(groupId, pageable)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long messageId) {
        chatMessageRepository.findById(messageId).ifPresent(msg -> {
            if (msg.getReceiverId().equals(userId)) {
                msg.setRead(true);
                chatMessageRepository.save(msg);
            }
        });
    }

    @Override
    public int getUnreadCount(Long userId) {
        return (int) chatMessageRepository.countByReceiverIdAndReadFalse(userId);
    }

    @Override
    public int getUnreadCount(Long userId, Long peerId) {
        return (int) chatMessageRepository.countByReceiverIdAndSenderIdAndReadFalse(userId, peerId);
    }

    private Message toDto(ChatMessage entity) {
        return new Message(
                entity.getId(),
                entity.getSenderId(),
                entity.getReceiverId(),
                entity.getGroupId(),
                entity.getContent(),
                entity.getSentAt().toEpochSecond(ZoneOffset.UTC) * 1000,
                entity.isRead(),
                entity.getClientMessageId()
        );
    }
}
