package jp.workwith.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthInterceptorTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsProtectedApisWithoutCreatingSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ログインが必要です。"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();

        mockMvc.perform(patch("/api/users/me/avatar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatarType\":\"male_a\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/users/logout"))
                .andExpect(status().isUnauthorized());

        // 新しく追加したROOMS APIも共通チェックの対象です。
        mockMvc.perform(get("/api/rooms/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsPublicApisAndStaticFilesWithoutLogin() throws Exception {
        // register/loginへ到達して400になるため、Interceptorの401ではありません。
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/main.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/css/login.css"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/js/login.js"))
                .andExpect(status().isOk());

        // ブラウザのCORS事前確認は、認証Interceptorでは拒否しません。
        mockMvc.perform(options("/api/users/me"))
                .andExpect(status().isOk());
    }
}
