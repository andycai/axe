package com.iwayee.activity.api.comp;

import io.vertx.core.json.JsonObject;

// 参与者
public class Player {
  public long id;
  public int sex;
  public String wx_nick;
  public String nick;

  public void fromUser(User user) {
    id = user.id;
    sex = user.sex;
    wx_nick = user.wx_nick;
    nick = user.nick;
  }

  public JsonObject toJson() {
    JsonObject jo = new JsonObject();
    jo.put("id", id)
      .put("wx_nick", wx_nick)
      .put("nick", nick)
      .put("sex", sex);
    return jo;
  }

  public void fromJson(JsonObject data) {
    id = data.getLong("id");
    wx_nick = data.getString("wx_nick");
    nick = data.getString("nick");
    sex = data.getInteger("sex");
  }
}
