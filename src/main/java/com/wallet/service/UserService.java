package com.wallet.service;

import com.wallet.dto.CreateUserRequest;
import com.wallet.dto.UserResponse;
import com.wallet.exception.DuplicateResourceException;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.model.User;
import com.wallet.model.Wallet;
import com.wallet.repository.UserRepository;
import com.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User with email '" + request.getEmail() + "' already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .build();

        user = userRepository.save(user);
        log.info("Created user id={} email={}", user.getId(), user.getEmail());

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();

        wallet = walletRepository.save(wallet);
        log.info("Created wallet id={} for user id={}", wallet.getId(), user.getId());

        return toResponse(user, wallet.getId());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> {
                    Wallet wallet = walletRepository.findByUserId(user.getId()).orElse(null);
                    return toResponse(user, wallet != null ? wallet.getId() : null);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        return toResponse(user, wallet != null ? wallet.getId() : null);
    }

    private UserResponse toResponse(User user, Long walletId) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .walletId(walletId)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
