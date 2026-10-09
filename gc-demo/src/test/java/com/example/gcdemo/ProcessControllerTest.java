package com.example.gcdemo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessControllerTest {

    private final ProcessController controller = new ProcessController();

    @Test
    void fixedEndpointProducesExpectedLength() {
        // Chỉ test bản đã sửa (bản lỗi mất hàng chục giây nên không đưa vào unit test)
        assertThat(controller.processDataFixed()).endsWith("Result length: 938890");
    }

    @Test
    void pingReturnsPong() {
        assertThat(controller.ping()).isEqualTo("pong");
    }
}
