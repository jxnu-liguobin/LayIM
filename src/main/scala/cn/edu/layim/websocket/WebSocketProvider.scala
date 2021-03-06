package cn.edu.layim.websocket

import akka.Done
import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.OverflowStrategy
import cn.edu.layim.actor.MessageHandleActor
import cn.edu.layim.actor.ScheduleJobActor
import cn.edu.layim.actor.UserStatusChangeActor
import cn.edu.layim.actor.ActorMessage._
import cn.edu.layim.constant.SystemConstant
import cn.edu.layim.service.RedisService
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * 基于 akka stream 和 akka http的 websocket
  *
 * @date 2020年01月27日
  * @author 梦境迷离
  * @version 1.2
  */
@Component
@DependsOn(Array("redisService"))
class WebSocketProvider @Autowired() (redisService: RedisService) {

  import ActorCommon._
  private final lazy val log: Logger = LoggerFactory.getLogger(classOf[WebSocketProvider])
  private final lazy val wsConnections = WebSocketService.actorRefSessions
  private lazy val msgActor = system.actorOf(Props(classOf[MessageHandleActor]))
  private lazy val jobActor = system.actorOf(Props(classOf[ScheduleJobActor]))
  private lazy val userStatusActor = system.actorOf(Props(classOf[UserStatusChangeActor]))

  //重连是3秒
  system.scheduler.schedule(5000 milliseconds, 10000 milliseconds, jobActor, OnlineUserMessage)

  /**
    * 处理连接与消息处理
    *
   * @param uId
    * @return
    */
  def openConnection(uId: Integer): Flow[Message, Message, NotUsed] = {
    //刷新重连
    //closeConnection(uId)
    val (actorRef: ActorRef, publisher: Publisher[TextMessage.Strict]) = {
      Source
        .actorRef[String](16, OverflowStrategy.fail)
        .map(TextMessage.Strict)
        .toMat(Sink.asPublisher(false))(Keep.both)
        .run()
    }
    val out = Source.fromPublisher(publisher)
    val in: Sink[Message, Unit] = {
      Flow[Message]
        .watchTermination()((_, ft) => ft.foreach { _ => closeConnection(uId) })
        .mapConcat {
          case TextMessage.Strict(message) =>
            msgActor ! TransmitMessage(uId, message, actorRef)
            Nil
          case _ => Nil
        }
        .to(Sink.ignore)
    }

    log.info(s"Opening websocket connection => [uid = $uId]")
    wsConnections.put(uId, actorRef)
    Flow.fromSinkAndSource(in, out)
  }

  /**
    * 关闭websocket
    *
   * @param id
    */
  def closeConnection(id: Integer) = {
    wsConnections.asScala.get(id).foreach { ar =>
      log.info(s"Closing websocket connection => [id = $id]")
      wsConnections.remove(id)
      redisService.removeSetValue(SystemConstant.ONLINE_USER, id + "")
//      userStatusChangeByServer(id, "hide")
      ar ! Status.Success(Done)
    }
  }
//
//  def userStatusChangeByServer(uId: Int, status: String): Unit = {
//    userStatusActor ! UserStatusChange(uId, status)
//  }
}
