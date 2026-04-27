package com.im.message.service;

import com.im.message.api.OfflineMessageService;
import com.im.message.api.dto.Message;
import com.im.message.entity.OfflineMessage;
import com.im.message.repository.OfflineMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class OfflineMessageServiceImpl implements OfflineMessageService {

    private final OfflineMessageRepository offlineMessageRepository;

    public OfflineMessageServiceImpl(OfflineMessageRepository offlineMessageRepository) {
        this.offlineMessageRepository = offlineMessageRepository;
    }

    @Override
    @Transactional
    public void storeOffline(Long userId, Message message) {
        OfflineMessage entity = new OfflineMessage();
        entity.setUserId(userId);
        entity.setMessageId(message.messageId());
        entity.setSenderId(message.senderId());
        entity.setGroupId(message.groupId());
        entity.setContent(message.content());
        entity.setSentAt(LocalDateTime.ofEpochSecond(message.timestamp() / 1000, 0, ZoneOffset.UTC));
        offlineMessageRepository.save(entity);
    }

    @Override
    public List<Message> pullOffline(Long userId) {
        return offlineMessageRepository
                .findByUserIdAndDeliveredAtIsNullOrderBySentAtAsc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void removeDelivered(Long userId, List<Long> messageIds) {
        List<OfflineMessage> list = offlineMessageRepository
                .findByUserIdAndDeliveredAtIsNullOrderBySentAtAsc(userId)
                .stream()
                .filter(om -> messageIds.contains(om.getMessageId()))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        for (OfflineMessage om : list) {
            om.setDeliveredAt(now);
        }
        offlineMessageRepository.saveAll(list);
    }

    private Message toDto(OfflineMessage entity) {
        return new Message(
                entity.getMessageId(),
                entity.getSenderId(),
                entity.getUserId(),
                entity.getGroupId(),
                entity.getContent(),
                entity.getSentAt().toEpochSecond(ZoneOffset.UTC) * 1000,
                false,
                ""
        );
    }
}
