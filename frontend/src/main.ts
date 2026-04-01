import { createApp } from "vue";
import Antd from "ant-design-vue";
import "ant-design-vue/dist/reset.css";

import App from "./App.vue";
import router from "./router";
import pinia from "./stores";
import { installConsoleCapture } from "./logging/consoleCapture";
import "./styles.css";

installConsoleCapture();

createApp(App).use(pinia).use(router).use(Antd).mount("#app");
