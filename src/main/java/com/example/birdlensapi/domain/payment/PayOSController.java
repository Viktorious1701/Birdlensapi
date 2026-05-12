package com.example.birdlensapi.domain.payment;

import com.example.birdlensapi.common.dto.ApiResponse;
import com.example.birdlensapi.domain.payment.dto.PaymentLinkRequest;
import com.example.birdlensapi.domain.payment.dto.PaymentLinkResponse;
import com.example.birdlensapi.domain.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payos")
public class PayOSController {

    private final PayOSService payOSService;

    public PayOSController(PayOSService payOSService) {
        this.payOSService = payOSService;
    }

    @PostMapping("/create-payment-link")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> createPaymentLink(
            @Valid @RequestBody PaymentLinkRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = ((User) userDetails).getId();

        PaymentLinkResponse response = payOSService.initiateSubscriptionPayment(request.subscriptionId(), userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}