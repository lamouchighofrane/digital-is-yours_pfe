package com.digitalisyours.domain.port.out;

public interface EmailSenderPort {
    void sendOtpVerification(String to, String prenom, String otpCode);
    void sendPasswordReset(String to, String prenom, String otpCode);
    void sendWelcome(String to, String prenom, String role);
}
