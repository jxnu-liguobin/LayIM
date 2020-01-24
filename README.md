### 主要技术 

* Scala
* Akka HTTP
* Spring Boot
* Redis
* Alibaba Druid
* Java Mail
* WebSocket
* Mybatis And PageHelper
* Swagger
* LayIM

### 环境 

* Scala 2.12.8+
* JDK 1.8
* Gradle 3.0
* Mysql
* Redis 

### 使用 

配置Mysql数据库，Redis以及邮件服务器，如果不需要邮件相关服务，可以在UserService.scala中注释掉相关的代码

* 创建MySQL库 `websocket`
* `schema.sql` 和 `data.sql` 自动初始化表结构和数据，如需要自己mock数据，参考 `RandomData.scala` 构造
* 修改 `application.conf` 
```
# 必须配置akka http websocket server的绑定IP，且不能与SpringBoot绑定的相同
# 这里我分别用了127.0.0.1:80 和 192.168.124.10:8080。暂时怎么搞
akka-http-server {
 host = "192.168.124.10"
 port = 8080
}
```
* 修改`webapp/static/js/websocket.js`
```js
var host = "192.168.124.10:8080" //改为与akka-http-server一致
```
* 启动 `Application.scala`
* 访问 `http://localhost`
* 登录 
```
选取t_user表中的任意一条数据，如：
邮箱 15906184943@sina.com
密码 123456（所有mock数据都是一个密码）
激活 将status状态改为 nonactivated（需要激活才能登录，要配置JavaMail）
```

> 默认每次启动Application会自动刷新数据库，需要保留记录，请为`schema.sql`和`data.sql`重命名

### 示例

![基于Akka HTTP的LayIM](https://github.com/jxnu-liguobin/LayIM/blob/v1.2/src/main/resources/layim.png)

### v1.2 版本

更新日志

* 使用Akka HTTP重构WebSocket通信
* 升级Scala版本至2.12.8

### V1.1 版本

更新日志

* 查询我创建的群接口 
* 退群接口完善 
* 创建群组接口 
* 更新个人信息接口 
* 加入群组接口 
* 删除群组接口 
* 修复分页查询bug 
* 修复Redis缓存bug 
* 管理群组接口 【重命名群、修改群信息】 
* 管理好友列表接口【重命名、删除、新增】
* 若干前端问题或bug
* 代码优化

创建新的群，默认将创建者加入群中。退群的操作可能有以下情况：

1. 不允许创建者退群。  √
2. 允许创建者退群，但在退出时默认删除群。需要重置回话并发送所有提醒给群内人。感觉不太好。
3. 创建者退群时，将群中创建者【群主】，更改为最早加入者，不影响会话，只需要发送系统提示【群主已变更】。
4. 群组列表的删除和增加只能通过刷新才能显示最新数据

参考[scalad](https://github.com/scalad/LayIM)，并二次开发，是为1.1版本，还存在许多bug！！

包括但不限增加、修改、删除、完善代码等