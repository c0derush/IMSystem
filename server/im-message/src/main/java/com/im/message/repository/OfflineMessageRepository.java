package com.im.message.repository;

import com.im.message.entity.OfflineMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OfflineMessageRepository extends JpaRepository<OfflineMessage, Long> {

    List<OfflineMessage> findByUserIdAndDeliveredAtIsNullOrderBySentAtAsc(Long userId);
}
