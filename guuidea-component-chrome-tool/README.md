## 浏览器代理工具

### 功能
这不是一个组件而是一个程序，使用者在程序中配置账号(如阿里云账号)和代理，在使用账号访问网页时(如使用阿里云账号登录控制台)
会通过与之绑定的代理发起请求。

### 环境
`JDK1.8`及以上

### 打包上传
1. 使用maven命令`deploy -Dmaven.test.skip=true`编译打包
2. 打包后在/target文件夹下生成同项目名带版本号的jar包

### 使用方法
1. 先配置代理
点击代理选项，选择新建代理，填写代理服务器IP和端口、密码等信息，点击提交。
2. 配置账号
点击账号选项，选择新建账号，填写账号信息和登录的网页URL，选择之前配置的代理。
3. 登录
配置好后点击一键登录即可登录对应网页。

 
