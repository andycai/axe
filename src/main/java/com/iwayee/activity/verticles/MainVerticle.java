package com.iwayee.activity.verticles;

import com.iwayee.activity.define.ErrCode;
import com.iwayee.activity.hub.Hub;
import com.iwayee.activity.api.system.*;
import com.iwayee.activity.hub.Some;
import com.iwayee.activity.utils.ConfigUtils;
import com.iwayee.activity.utils.HttpUtils;
import com.iwayee.activity.utils.Singleton;
import com.iwayee.activity.utils.TokenExpiredException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.function.Consumer;

public class MainVerticle extends AbstractVerticle {
  private Router router;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Hub.getInstance().vertx = vertx;
    ConfigUtils.getConfig(vertx, () -> {
      startServer();
    });
  }

  private void errAuth(RoutingContext ctx) {
    var ret = ErrCode.ERR_AUTH;
    ctx.json(new JsonObject().put("code", ret.getErrorCode()).put("msg", ret.getErrorDesc()));
  }

  private void errArg(RoutingContext ctx) {
    var ret = ErrCode.ERR_PARAM;
    ctx.json(new JsonObject().put("code", ret.getErrorCode()).put("msg", ret.getErrorDesc()));
  }

  private void route(String s, Consumer<Some> action) {
    route(s, action, true);
  }

  private void runAction(RoutingContext ctx, Consumer<Some> action, boolean auth) {
    try {
      var some = new Some(ctx);
      if (auth) {
        some.checkToken();
      }
      action.accept(some);
    } catch (IllegalArgumentException e) {
      errArg(ctx);
    } catch (TokenExpiredException e) {
      errAuth(ctx);
    }
  }

  private void route(String s, Consumer<Some> action, boolean auth) {
    router.route(s).handler(ctx -> {
      runAction(ctx, action, auth);
    });
  }

  private void get(String s, Consumer<Some> action) {
    get(s, action, true);
  }

  private void get(String s, Consumer<Some> action, boolean auth) {
    router.get(s).handler(ctx -> {
      runAction(ctx, action, auth);
    });
  }

  private void post(String s, Consumer<Some> action) {
    post(s, action, true);
  }

  private void post(String s, Consumer<Some> action, boolean auth) {
    router.post(s).handler(ctx -> {
      runAction(ctx, action, auth);
    });
  }

  private void put(String s, Consumer<Some> action) {
    put(s, action, true);
  }

  private void put(String s, Consumer<Some> action, boolean auth) {
    router.put(s).handler(ctx -> {
      runAction(ctx, action, auth);
    });
  }

  private void delete(String s, Consumer<Some> action) {
    delete(s, action, true);
  }

  private void delete(String s, Consumer<Some> action, boolean auth) {
    router.delete(s).handler(ctx -> {
      runAction(ctx, action, auth);
    });
  }

  private void startServer() {
    router = Router.router(vertx);
    var test = Singleton.instance(TestSystem.class);
    var user = Singleton.instance(UserSystem.class);
    var group = Singleton.instance(GroupSystem.class);
    var act = Singleton.instance(ActivitySystem.class);

//    router.route().handler(CookieHandler.create());
//    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    router.route().handler(BodyHandler.create());
    //to make session work correctly on Huawei & Mi phone, set Cookie Path to root '/'
//    Set<Cookie> ccList=routingContext.cookies();
//    for(Cookie ck:ccList){
//      ck.setPath("/");
//    }

    // 测试
    route("/test/:case", test::exec);
    route("/test", test::exec);

    // 用户
    get("/users/:uid", user::getUser);
    get("/users/your/groups", group::getGroupsByUserId);
    get("/users/your/activities", act::getActivitiesByUserId);

    post("/login", user::login, false);
    post("/login_wx", user::wxLogin, false);
    post("/register", user::register, false);
    post("/logout", user::logout);

    // 群组
    get("/groups/:gid", group::getGroupById);
    get("/groups", group::getGroups);
    get("/groups/:gid/pending", group::getApplyList);
    get("/groups/:gid/activities", act::getActivitiesByGroupId);

    post("/groups", group::createGroup);
    post("/groups/:gid/apply", group::apply);
    post("/groups/:gid/approve", group::approve);
    post("/groups/:gid/promote/:mid", group::promote);
    post("/groups/:gid/transfer/:mid", group::transfer);

    put("/groups/:gid", group::updateGroup);

    // 活动
    get("/activities/:aid", act::getActivityById);
    get("/activities", act::getActivities);

    post("/activities", act::createActivity);
    post("/activities/:aid/end", act::endActivity);
    post("/activities/:aid/apply", act::applyActivity);
    post("/activities/:aid/cancel", act::cancelActivity);

    put("/activities/:aid", act::updateActivity);

    HttpUtils.startServer(vertx, router);
  }
}
