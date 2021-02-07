package com.iwayee.activity.api.system;

import com.iwayee.activity.api.comp.Activity;
import com.iwayee.activity.api.comp.Group;
import com.iwayee.activity.hub.ActivityCache;
import com.iwayee.activity.define.ActivityFeeType;
import com.iwayee.activity.define.ActivityStatus;
import com.iwayee.activity.define.RetCode;
import com.iwayee.activity.hub.Some;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

// 根据用户获取活动
public class ActivitySystem extends BaseSystem {
  public void getActivitiesByUserId(Some some) {
    var uid = some.userId();
    // 用户数据
    cache().user().getUserById(uid, user -> {
      if (user == null) {
        some.err(RetCode.ERR_DATA);
        return;
      }
      if (user.activities.size() <= 0) {
        some.ok(new JsonArray());
        return;
      }
      // 活动数据
      cache().act().getActivitiesByIds(user.activities.getList(), acts -> {
        var jr = new JsonArray();
        acts.forEach((key, value) -> {
          jr.add(value.toJson());
        });
        some.ok(jr);
      });
    });
  }

  // 根据群组获取活动
  public void getActivitiesByGroupId(Some some) {
    var gid = some.getInt("gid");

    // 群组数据
    cache().group().getGroupById(gid, data -> {
      if (data == null) {
        some.err(RetCode.ERR_DATA);
        return;
      }
      if (data.activities.size() <= 0) {
        some.ok(new JsonArray());
        return;
      }
      // 活动数据
      cache().act().getActivitiesByIds(data.activities.getList(), acts -> {
        var jr = new JsonArray();
        acts.forEach((key, value) -> {
          jr.add(value.toJson());
        });
        some.ok(jr);
      });
    });
  }

  // 根据类型获取活动
  public void getActivities(Some some) {
    var type = some.jsonUint("type");
    var status = some.jsonUint("status");
    var page = some.jsonUint("page");
    var num = some.jsonUint("num");

    cache().act().getActivitiesByType(type, status, page, num, acts -> {
      var jr = new JsonArray();
      acts.forEach((key, value) -> {
        jr.add(value.toJson());
      });
      some.ok(jr);
    });
  }

  // 获得单个活动数据
  public void getActivityById(Some some) {
    var aid = some.getUint("aid");

    cache().act().getActivityById(aid, activity -> {
      if (activity == null || activity.queue == null || activity.queue.size() <= 0) {
        some.err(RetCode.ERR_DATA);
      } else {
        cache().user().getUsersByIds(activity.queue.getList(), users -> {
          if (users == null) {
            some.err(RetCode.ERR_DATA);
            return;
          }
          var players = cache().user().toPlayer(users);
          var ret = JsonObject.mapFrom(activity);
          ret.put("players", players);
          some.ok(ret);
        });
      }
    });
  }

  private void doCreateActivity(Some some, JsonObject jo, int uid, Group group) {
    cache().act().createActivity(jo, lastInsertId -> {
      if (lastInsertId > 0) {
        cache().user().getUserById(uid, user -> {
          if (user != null) {
            // 用户活动列表更新
            user.addActivity(lastInsertId.intValue());
            cache().user().syncToDB(uid, b -> {
              if (b) {
                // 群组活动列表更新
                if (group != null) {
                  group.addActivity(lastInsertId.intValue());
                  cache().group().syncToDB(group.id, b2 -> {
                    if (b2) {
                      some.ok((new JsonObject()).put("activity_id", lastInsertId));
                      return;
                    }
                    some.err(RetCode.ERR_ACTIVITY_CREATE);
                  });
                  return;
                }
                some.ok((new JsonObject()).put("activity_id", lastInsertId));
                return;
              }
              some.err(RetCode.ERR_ACTIVITY_CREATE);
            });
            return;
          }
          some.err(RetCode.ERR_ACTIVITY_CREATE);
        });
      } else {
        some.err(RetCode.ERR_ACTIVITY_CREATE);
      }
    });
  }

  // 创建获得活动
  public void createActivity(Some some) {
    var planner = some.userId(); // 通过 session 获取
    var jo = new JsonObject();
    jo.put("planner", planner);
    jo.put("group_id", some.jsonInt("group_id"));
    jo.put("kind", some.jsonUint("kind"));
    jo.put("type", some.jsonUint("type"));
    jo.put("quota", some.jsonUint("quota"));
    jo.put("fee_type", some.jsonUint("fee_type"));
    jo.put("fee_male", some.jsonInt("fee_male"));
    jo.put("fee_female", some.jsonInt("fee_female"));
    jo.put("ahead", some.jsonUint("ahead"));
    jo.put("title", some.jsonStr("title"));
    jo.put("remark", some.jsonStr("remark"));
    jo.put("addr", some.jsonStr("addr"));
    jo.put("begin_at", some.jsonStr("begin_at"));
    jo.put("end_at", some.jsonStr("end_at"));

    // 活动前结算，必须填写费用
    if (some.jsonUint("fee_type") == ActivityFeeType.FEE_TYPE_BEFORE.ordinal()
      && (some.jsonInt("fee_male") == 0 || some.jsonInt("fee_female") == 0)
    ) {
      some.err(RetCode.ERR_ACTIVITY_FEE);
      return;
    }

    jo.put("queue", String.format("[%d]", planner));
    jo.put("queue_sex", String.format("[%d]", planner));
    jo.put("status", ActivityStatus.DOING.ordinal());

    // 群活动必须要群管理员才能创建
    var gid = some.jsonInt("group_id");
    if (gid > 0) {
      cache().group().getGroupById(gid, group -> {
        if (!group.isManager(some.userId())) {
          some.err(RetCode.ERR_ACTIVITY_CANNOT_APPLY_NOT_IN_GROUP);
          return;
        }
        doCreateActivity(some, jo, some.userId(), group);
      });
      return;
    }
    doCreateActivity(some, jo, some.userId(), null);
  }

  private void doUpdateActivity(Some some, Activity activity) {
    cache().act().syncToDB(activity.id, b -> {
      if (!b) {
        some.err(RetCode.ERR_ACTIVITY_UPDATE);
        return;
      }
      some.succeed();
    });
  }

  public void updateActivity(Some some) {
    var aid = some.getUint("aid");
    var quota = some.jsonUint("quota");
    var ahead = some.jsonUint("ahead");
    var fee_male = some.jsonInt("fee_male");
    var fee_female = some.jsonInt("fee_female");
    var title = some.jsonStr("title");
    var remark = some.jsonStr("remark");
    var addr = some.jsonStr("addr");
    var begin_at = some.jsonStr("begin_at");
    var end_at = some.jsonStr("end_at");

    ActivityCache.getInstance().getActivityById(aid, activity -> {
      if (activity == null) {
        some.err(RetCode.ERR_ACTIVITY_NO_DATA);
        return;
      }
      activity.quota = quota;
      activity.ahead = ahead;
      activity.fee_male = fee_male;
      activity.fee_female = fee_female;
      activity.title = title;
      activity.remark = remark;
      activity.addr = addr;
      activity.begin_at = begin_at;
      activity.end_at = end_at;

      // 群活动必须要群管理员才能更新
      var gid = activity.group_id;
      if (gid > 0) {
        cache().group().getGroupById(gid, group -> {
          if (!group.isManager(some.userId())) {
            some.err(RetCode.ERR_ACTIVITY_CANNOT_APPLY_NOT_IN_GROUP);
            return;
          }
          doUpdateActivity(some, activity);
        });
        return;
      }

      if (activity.planner != some.userId()) {
        some.err(RetCode.ERR_ACTIVITY_NOT_PLANNER);
        return;
      }
      doUpdateActivity(some, activity);
    });
  }

  public void endActivity(Some some) {
    var aid = some.getUint("aid");
    var close = some.jsonBool("close"); // 是否结算活动，非结算代表手动终止
    var inGroup = some.jsonBool("in_group"); // 是否在群组活动
    var status = close ? ActivityStatus.DONE.ordinal() : ActivityStatus.END.ordinal();

    if (inGroup) {
      // 是否群管理员
    } else {
      //
    }
    if (close) { // 结算，计算男女的费用
      //
    }

    // TODO:只有发起者或者群管理员才能结束活动
    dao().act().updateActivityStatus(aid, status, b -> {
      if (b) {
        some.ok(new JsonObject().put("activity_id", aid));
      } else {
        some.err(RetCode.ERR_OP);
      }
    });
  }

  private void enqueue(Some some, Activity activity) {
    activity.queue.add(some.userId());
    cache().act().syncToDB(activity.id, b -> {
      if (!b) {
        some.err(RetCode.ERR_ACTIVITY_UPDATE);
        return;
      }
      some.succeed();
    });
  }

  /**
   * 报名
   */
  public void applyActivity(Some some) {
    var aid = some.getUint("aid");

    ActivityCache.getInstance().getActivityById(aid, activity -> {
      if (activity == null) {
        some.err(RetCode.ERR_ACTIVITY_NO_DATA);
        return;
      }
      // 必须是群组成员
      if (activity.group_id > 0) {
        cache().group().getGroupById(activity.group_id, group -> {
          if (!group.isMember(some.userId())) {
            some.err(RetCode.ERR_ACTIVITY_CANNOT_APPLY_NOT_IN_GROUP);
            return;
          }
          enqueue(some, activity);
        });
        return;
      }
      enqueue(some, activity);
    });
  }

  public void cancelActivity(Some some) {
    var aid = some.getInt("aid");

    ActivityCache.getInstance().getActivityById(aid, activity -> {
      if (activity == null) {
        some.err(RetCode.ERR_ACTIVITY_NO_DATA);
        return;
      }
      // 必须是群组成员
      if (activity.group_id > 0) {
        cache().group().getGroupById(activity.group_id, group -> {
          if (!group.isMember(some.userId())) {
            some.err(RetCode.ERR_ACTIVITY_CANNOT_APPLY_NOT_IN_GROUP);
            return;
          }
          enqueue(some, activity);
        });
        return;
      }
      enqueue(some, activity);
    });
  }

  /**
   * 带人
   */
  public void bringActivity(Some some) {
    var aid = some.getInt("aid");
  }
}