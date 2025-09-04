import { defineConfig } from "umi";
import routes from "./routes";

export default defineConfig({
  // port: 8000,
  hash: false,
  history: {
    type: 'hash',
  },
  /**
   * @name 路由的配置，不在路由中引入的文件不会编译
   * @description 只支持 path，component，routes，redirect，wrappers，title 的配置
   * @doc https://umijs.org/docs/guides/routes
   */
  // umi routes: https://umijs.org/docs/routing
  routes,
  npmClient: "yarn",
  tailwindcss: {},
  plugins: ["@umijs/plugins/dist/tailwindcss"],
  mfsu: {
    strategy: "normal",
  },
});