package com.suriya.payments.repository;

import com.suriya.payments.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {}
