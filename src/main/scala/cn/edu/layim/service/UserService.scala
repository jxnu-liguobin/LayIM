package cn.edu.layim.service

import java.util
import java.util.List

import cn.edu.layim.constant.SystemConstant
import cn.edu.layim.domain._
import cn.edu.layim.entity._
import cn.edu.layim.repository.UserRepository
import cn.edu.layim.util.DateUtil
import cn.edu.layim.util.SecurityUtil
import cn.edu.layim.util.UUIDUtil
import cn.edu.layim.util.WebUtil
import javax.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import scala.collection.JavaConverters._

/**
  * 用户信息相关操作
  *
 * @date 2018年9月9日
  * @author 梦境迷离
  *
 */
@Service
class UserService @Autowired() (userRepository: UserRepository, mailService: MailService) {

  private final val LOGGER: Logger = LoggerFactory.getLogger(classOf[UserService])

  /**
    * 退出群
    *
   * @param gid 群组id
    * @param uid 用户
    * @return Boolean
    */
  @CacheEvict(
    value = Array("findUserById", "findGroupsById", "findUserByGroupId"),
    allEntries = true
  )
  @Transactional
  def leaveOutGroup(gid: Int, uid: Int): Boolean = {

    //创建者退群，直接解散群,此处逻辑可自行调整
    val group = userRepository.findGroupById(gid)
    if (group == null) return false
    if (group.getCreateId.equals(uid)) false
    else userRepository.leaveOutGroup(new GroupMember(gid, uid)) == 1
  }

  /**
    * 添加群成员
    *
   * @param gid          群组id
    * @param uid          用户id
    * @param messageBoxId 消息盒子Id
    * @return Boolean
    */
  @Transactional
  @CacheEvict(value = Array("findUserByGroupId", "findGroupsById"), allEntries = true)
  def addGroupMember(gid: Int, uid: Int, messageBoxId: Int): Boolean = {
    val group = userRepository.findGroupById(gid)
    if (group != null && group.getCreateId.equals(uid)) {
      //自己加自己的群，默认同意
      updateAddMessage(messageBoxId, 1)
      true
    } else {
      userRepository.addGroupMember(new GroupMember(gid, uid)) == 1
      updateAddMessage(messageBoxId, 1)
    }
  }

  /**
    * 用户创建群时，将自己加入群组，不需要提示
    *
   * @param gid 群组id
    * @param uid 用户id
    * @return Boolean
    */
  @CacheEvict(value = Array("findGroupsById", "findUserByGroupId"), allEntries = true)
  @Transactional
  def addGroupMember(gid: Int, uid: Int): Boolean = {
    userRepository.addGroupMember(new GroupMember(gid, uid)) == 1
  }

  /**
    * 删除好友
    *
   * @param friendId 好友id
    * @param uId      个人/用户id
    * @return Boolean
    */
  @CacheEvict(
    value = Array("findUserById", "findFriendGroupsById", "findUserByGroupId"),
    allEntries = true
  )
  @Transactional
  def removeFriend(friendId: Int, uId: Int): Boolean = {
    userRepository.removeFriend(friendId, uId) == 1
  }

  /**
    * 更新用户头像
    *
   * @param userId 个人id
    * @param avatar 头像
    * @return Boolean
    */
  @CacheEvict(value = Array("findUserById"), allEntries = true)
  @Transactional
  def updateAvatar(userId: Int, avatar: String): Boolean = {
    userRepository.updateAvatar(userId, avatar) == 1
  }

  /**
    * 更新用户信息
    *
   * @param user 个人信息
    * @return Boolean
    */
  @CacheEvict(
    value = Array("findUserById", "findUserByGroupId", "findFriendGroupsById"),
    allEntries = true
  )
  @Transactional
  def updateUserInfo(user: User): Boolean = {
    userRepository.updateUserInfo(user) == 1
  }

  /**
    * 更新用户状态
    *
   * @param user 个人信息
    * @return Boolean
    */
  @CacheEvict(
    value = Array("findUserById", "findUserByGroupId", "findFriendGroupsById"),
    allEntries = true
  )
  @Transactional
  def updateUserStatus(user: User): Boolean = {
    userRepository.updateUserStatus(user) == 1
  }

  /**
    * 移动好友分组
    *
   * @param groupId 新的分组id
    * @param uId     被移动的好友id
    * @param mId     我的id
    * @return Boolean
    */
  //清除缓存
  @CacheEvict(
    value = Array("findUserById", "findFriendGroupsById", "findUserByGroupId"),
    allEntries = true
  )
  @Transactional
  def changeGroup(groupId: Int, uId: Int, mId: Int): Boolean = {
    userRepository.changeGroup(groupId, uId, mId) == 1
  }

  /**
    * 添加好友操作
    *
   * @param mid          我的id
    * @param mgid         我设定的分组
    * @param tid          对方的id
    * @param tgid         对方设定的分组
    * @param messageBoxId 消息盒子的消息id
    * @return Boolean
    */
  @Transactional
  @CacheEvict(
    value = Array("findUserById", "findFriendGroupsById", "findUserByGroupId"),
    allEntries = true
  )
  def addFriend(mid: Int, mgid: Int, tid: Int, tgid: Int, messageBoxId: Int): Boolean = {
    val add = new AddFriends(mid, mgid, tid, tgid)
    try {
      if (userRepository.addFriend(add) != 0) updateAddMessage(messageBoxId, 1)
      else false
    } catch {
      case ex: Exception => {
        LOGGER.error("重复添好友")
        false
      }
    }
  }

  /**
    * 创建好友分组列表
    *
   * @param uid       个人id
    * @param groupname 群组id
    * @return Boolean FriendGroup
    */
  @CacheEvict(value = Array("findFriendGroupsById"), allEntries = true)
  @Transactional
  def createFriendGroup(groupname: String, uid: Int): Boolean = {
    userRepository.createFriendGroup(new FriendGroup(uid, groupname)) == 1
  }

  /**
    * 创建群组
    *
   * @param groupList 群
    * @return Boolean
    */
  @CacheEvict(value = Array("findGroupsById"), allEntries = true)
  @Transactional
  def createGroup(groupList: GroupList): Int = {
    if (groupList == null) -1
    else {
      userRepository.createGroupList(groupList)
      val id = groupList.getId
      if (id > 0) id else -1
    }
  }

  /**
    * 统计未处理消息
    *
   * @param uid   个人id
    * @param agree 0未处理，1同意，2拒绝
    * @return Int
    */
  def countUnHandMessage(uid: Int, agree: Integer): Int =
    userRepository.countUnHandMessage(uid, agree)

  /**
    * 查询添加好友、群组信息
    *
   * @param uid 个人id
    * @return List[AddInfo]
    */
  def findAddInfo(uid: Int): util.List[AddInfo] = {
    val list = userRepository.findAddInfo(uid)
    list.asScala.foreach { info =>
      {
        if (info.Type == 0) {
          info.setContent("申请添加你为好友")
        } else {
          val group: GroupList = userRepository.findGroupById(info.getFrom_group)
          if (group != null) {
            info.setContent("申请加入 '" + group.getGroupname + "' 群聊中!")
          }
        }
        info.setHref(null)
        info.setUser(findUserById(info.getFrom))
        LOGGER.info(info.toString())
      }
    }
    list
  }

  /**
    * 更新好友、群组信息请求
    *
   * @param messageBoxId 消息盒子id
    * @param agree        0未处理，1同意，2拒绝
    * @return Boolean
    */
  @Transactional
  def updateAddMessage(messageBoxId: Int, agree: Int): Boolean = {
    val addMessage = new AddMessage
    addMessage.setAgree(agree)
    addMessage.setId(messageBoxId)
    userRepository.updateAddMessage(addMessage) == 1
  }

  @Transactional
  def readFriendMessage(mine: Int, to: Int): Boolean = {
    userRepository.readMessage(mine, to, "friend") == 1
  }

  /**
    * 添加好友、群组信息请求
    *
   * @param addMessage 添加好友、群组信息对象
    * @see AddMessage.scala
    * @return Int
    */
  @Transactional
  def saveAddMessage(addMessage: AddMessage): Int = userRepository.saveAddMessage(addMessage)

  /**
    * 根据群名模糊统计
    *
   * @param groupName 群组名称
    * @return Int
    */
  def countGroup(groupName: String): Int = userRepository.countGroup(groupName)

  /**
    * 根据群名模糊查询群
    *
   * @param groupName 群组名称
    * @return List[GroupList]
    */
  def findGroup(groupName: String): util.List[GroupList] = userRepository.findGroup(groupName)

  /**
    * 根据用户名和性别统计用户
    *
   * @param username 用户名
    * @param sex      性别
    * @return Int
    */
  def countUsers(username: String, sex: Integer): Int = userRepository.countUser(username, sex)

  /**
    * 根据用户名和性别查询用户
    *
   * @param username 用户名
    * @param sex      性别
    * @return List[User]
    */
  def findUsers(username: String, sex: Integer): util.List[User] =
    userRepository.findUsers(username, sex)

  /**
    * 统计查询消息
    *
   * @param uid  消息所属用户id、用户个人id
    * @param mid  来自哪个用户
    * @param Type 消息类型，可能来自friend或者group
    * @return Int
    */
  def countHistoryMessage(uid: Int, mid: Int, Type: String): Int = {
    Type match {
      case "friend" => userRepository.countHistoryMessage(uid, mid, Type)
      case "group" => userRepository.countHistoryMessage(null, mid, Type)
    }
  }

  /**
    * 查询历史消息
    *
   * @param user 所属用户、用户个人
    * @param mid  来自哪个用户
    * @param Type 消息类型，可能来自friend或者group
    * @see User.scala
    * @return List[ChatHistory]
    */
  def findHistoryMessage(user: User, mid: Int, Type: String): util.List[ChatHistory] = {
    val list = new util.ArrayList[ChatHistory]()
    //单人聊天记录
    if ("friend".equals(Type)) {
      //查找聊天记录
      val historys: List[Receive] = userRepository.findHistoryMessage(user.getId, mid, Type)
      val toUser = findUserById(mid)
      historys.asScala.foreach { history =>
        {
          var chatHistory: ChatHistory = null
          if (history.getId == mid) {
            chatHistory = new ChatHistory(
              history.getId,
              toUser.getUsername,
              toUser.getAvatar,
              history.getContent,
              history.getTimestamp
            )
          } else {
            chatHistory = new ChatHistory(
              history.getId,
              user.getUsername,
              user.getAvatar,
              history.getContent,
              history.getTimestamp
            )
          }
          list.add(chatHistory)
        }
      }
    }
    //群聊天记录
    if ("group".equals(Type)) {
      //查找聊天记录
      val historys = userRepository.findHistoryMessage(null, mid, Type)
      historys.asScala.foreach { history =>
        {
          var chatHistory: ChatHistory = null
          val u = findUserById(history.getFromid)
          if (history.getFromid.equals(user.getId)) {
            chatHistory = new ChatHistory(
              user.getId,
              user.getUsername,
              user.getAvatar,
              history.getContent,
              history.getTimestamp
            )
          } else {
            chatHistory = new ChatHistory(
              history.getId,
              u.getUsername,
              u.getAvatar,
              history.getContent,
              history.getTimestamp
            )
          }
          list.add(chatHistory)
        }
      }
    }
    list
  }

  /**
    * 查询离线消息
    *
   * @param uid    消息所属用户id、用户个人id
    * @param status 历史消息还是离线消息 0代表离线 1表示已读
    * @return List[Receive]
    */
  def findOffLineMessage(uid: Int, status: Int): util.List[Receive] =
    userRepository.findOffLineMessage(uid, status)

  /**
    * 保存用户聊天记录
    *
   * @param receive 聊天记录信息
    * @see Receive.scala
    * @return Int
    */
  @Transactional
  def saveMessage(receive: Receive): Int = userRepository.saveMessage(receive)

  /**
    * 用户更新签名
    *
   * @param user 消息所属用户、用户个人
    * @see User.scala
    * @return Boolean
    */
  @Transactional
  def updateSing(user: User): Boolean = {
    if (user == null || user.getSign == null) false
    else userRepository.updateSign(user.getSign, user.getId) == 1
  }

  /**
    * 激活码激活用户
    *
   * @param activeCode 激活码
    * @return Int
    */
  def activeUser(activeCode: String): Int = {
    if (activeCode == null || "".equals(activeCode)) 0
    else
      userRepository.activeUser(activeCode)
  }

  /**
    * 判断邮件是否存在
    *
   * @param email 邮箱
    * @return Boolean
    */
  def existEmail(email: String): Boolean = {
    if (email == null || "".equals(email)) false
    else userRepository.matchUser(email) != null
  }

  /**
    * 用户邮件和密码是否匹配
    *
   * @param user 用户
    * @see User.scala
    * @return User
    */
  def matchUser(user: User): User = {
    if (user == null || user.getEmail == null)
      null
    else {
      val u: User = userRepository.matchUser(user.getEmail)
      //密码不匹配
      if (u == null || !SecurityUtil.matchs(user.getPassword, u.getPassword)) {
        null
      } else u
    }
  }

  /**
    * 根据群组ID查询群里用户的信息
    *
   * @param gid 群组id
    * @return List[User]
    */
  @Cacheable(value = Array("findUserByGroupId"), keyGenerator = "wiselyKeyGenerator")
  def findUserByGroupId(gid: Int): util.List[User] = userRepository.findUserByGroupId(gid)

  /**
    * 根据ID查询用户的好友分组的列表信息
    *
   * FriendList表示一个好友列表，一个用户可以有多个FriendList
    *
   * @param uid 用户ID
    * @return List[FriendList]
    */
  @Cacheable(value = Array("findFriendGroupsById"), keyGenerator = "wiselyKeyGenerator")
  def findFriendGroupsById(uid: Int): util.List[FriendList] = {
    val friends = userRepository.findFriendGroupsById(uid)
    //封装分组列表下的好友信息
    friends.asScala.foreach { friend: FriendList =>
      {
        friend.list = userRepository.findUsersByFriendGroupIds(friend.getId)
      }
    }
    friends
  }

  /**
    * 根据ID查询用户信息
    *
   * @param id 用户id
    * @return User
    */
  @Cacheable(value = Array("findUserById"), keyGenerator = "wiselyKeyGenerator")
  def findUserById(id: Int): User = {
    userRepository.findUserById(id)
  }

  /**
    * 根据用户ID查询用户的群组列表
    *
   * @param id 群组id
    * @return List[GroupList]
    */
  @Cacheable(value = Array("findGroupsById"), keyGenerator = "wiselyKeyGenerator")
  def findGroupsById(id: Int): util.List[GroupList] = {
    userRepository.findGroupsById(id)
  }

  /**
    * 保存用户信息
    *
   * @param user 用户
    * @see User.scala
    * @return Boolean
    */
  //清除缓存
  @CacheEvict(
    value = Array("findUserById", "findFriendGroupsById", "findUserByGroupId"),
    allEntries = true
  )
  @Transactional
  def saveUser(user: User, request: HttpServletRequest): Boolean = {
    if (
      user == null || user.getUsername == null || user.getPassword == null || user.getEmail == null
    )
      false
    else {
      //激活码
      val activeCode = UUIDUtil.getUUID64String()
      user.setActive(activeCode)
      user.setCreateDate(DateUtil.getDate)
      //加密密码
      user.setPassword(SecurityUtil.encrypt(user.getPassword))
      userRepository.saveUser(user)
      LOGGER.info("userid = " + user.getId)
      //创建默认的好友分组
      createFriendGroup(SystemConstant.DEFAULT_GROUP_NAME, user.getId)
      //发送激活电子邮件
      mailService.sendHtmlMail(
        user.getEmail,
        SystemConstant.SUBJECT,
        user.getUsername + ",请确定这是你本人注册的账号   " + ", " + WebUtil.getServerIpAdder(
          request
        ) + "/user/active/" + activeCode
      )
      true
    }
  }

}
