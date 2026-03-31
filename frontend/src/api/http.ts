import axios, { AxiosError } from "axios";
import { message } from "ant-design-vue";

const baseURL = import.meta.env.VITE_API_BASE_URL || "";

export const http = axios.create({
    baseURL,
    timeout: 30000
});

// 请求拦截：可加 token
http.interceptors.request.use((config) => {
    const token = localStorage.getItem("auth_token");
    const tokenType = localStorage.getItem("auth_token_type") || "Bearer";
    if (token) config.headers.Authorization = `${tokenType} ${token}`;
    return config;
});

// 响应拦截：统一错误提示
http.interceptors.response.use(
    (resp) => resp,
    (err: AxiosError<any>) => {
        const msg =
            (err.response?.data && (err.response.data.message || err.response.data.msg)) ||
            err.message ||
            "网络错误";
        message.error(msg);
        return Promise.reject(err);
    }
);
