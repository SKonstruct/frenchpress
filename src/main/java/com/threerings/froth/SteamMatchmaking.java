package com.threerings.froth;

import java.util.logging.Logger;

/**
 * Shim — SK references these enums/callback interfaces as types but never
 * invokes any method here, so the bodies all throw rather than pretend to
 * succeed.
 */
public class SteamMatchmaking
{
  private static final Logger log = Logger.getLogger(SteamMatchmaking.class.getName());

  public enum LobbyType {
    PRIVATE, FRIENDS_ONLY, PUBLIC, INVISIBLE, PRIVATE_UNIQUE
  }

  public enum Result { OK, NO_CONNECTION, TIMEOUT, FAIL, ACCESS_DENIED, LIMIT_EXCEEDED }

  public enum ChatRoomEnterResponse {
    SUCCESS, DOESNT_EXIST, NOT_ALLOWED, FULL, ERROR,
    BANNED, LIMITED, CLAN_DISABLED, COMMUNITY_BAN,
    MEMBER_BLOCKED_YOU, YOU_BLOCKED_MEMBER,
    RATELIMIT_EXCEEDED
  }

  public interface CreateLobbyCallback
  {
    void createLobbyResponse (Result result, long steamIdLobby);
  }

  public interface EnterLobbyCallback
  {
    void enterLobbyResponse (
      long steamIdLobby, int chatPermissions, boolean locked,
      ChatRoomEnterResponse response);
  }

  public interface GameLobbyJoinRequestCallback
  {
    void gameLobbyJoinRequest (long steamIdLobby, long steamIdFriend);
  }

  private static void warn () {
    log.warning("[frenchpress] WARNING: SteamMatchmaking method called but stubbed/unused");
  }

  public static void addGameLobbyJoinRequestCallback (GameLobbyJoinRequestCallback callback) {
    warn();
  }
  public static void removeGameLobbyJoinRequestCallback (GameLobbyJoinRequestCallback callback) {
    warn();
  }

  public static void createLobby (LobbyType type, int maxMembers, CreateLobbyCallback callback) {
    warn();
  }
  public static void joinLobby (long steamIdLobby, EnterLobbyCallback callback) {
    warn();
  }
  public static void leaveLobby (long steamIdLobby) {
    warn();
  }
  public static boolean inviteUserToLobby (long steamIdLobby, long steamIdInvitee) {
    warn();
    return false;
  }
  public static String getLobbyData (long steamIdLobby, String key) {
    warn();
    return "";
  }
  public static boolean setLobbyData (long steamIdLobby, String key, String value) {
    warn();
    return false;
  }
}
