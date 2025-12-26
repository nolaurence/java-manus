export default [
    { path: "/", component: "HomePage" },
    { path: "/docs", component: "docs" },
    { path: "/chat", component: "ChatPage" },
    { path: "/chat/:agentId", component: "ChatPage" },
    { path: "/settings", component: "SettingsPage" },
    { path: '*', layout: false, component: './404' },
];