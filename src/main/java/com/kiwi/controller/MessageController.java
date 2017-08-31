package com.kiwi.controller;

import com.kiwi.model.ShopInfo;
import com.kiwi.postgre.ConnectionProvider;
import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.Action;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.event.*;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import retrofit2.Response;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@LineMessageHandler
public class MessageController {

    @EventMapping
    public void eventHandle(Event event) throws Exception {

        if (event instanceof MessageEvent) {
            final MessageEvent<?> messageEvent = (MessageEvent<?>) event;
            log.info("Text Event start");
            if (messageEvent.getMessage() instanceof TextMessageContent) {
                handleTextMessageEvent((MessageEvent<TextMessageContent>) event);
            }

        } else if (event instanceof UnfollowEvent) {
            UnfollowEvent unfollowEvent = (UnfollowEvent) event;
            //handleUnfollowEvent(unfollowEvent);

        } else if (event instanceof FollowEvent) {
            FollowEvent followEvent = (FollowEvent) event;
            //reply(handleFollowEvent(followEvent));

        } else if (event instanceof JoinEvent) {
            final JoinEvent joinEvent = (JoinEvent) event;
            //reply(handleJoinEvent(joinEvent));

        } else if (event instanceof LeaveEvent) {
            final LeaveEvent leaveEvent = (LeaveEvent) event;
            //handleLeaveEvent(leaveEvent);

        } else if (event instanceof PostbackEvent) {
            final PostbackEvent postbackEvent = (PostbackEvent) event;
            //reply(handlePostbackEvent(postbackEvent));
            log.info("Postback Event start");

            // area存在チェック
            String postackData = postbackEvent.getPostbackContent().getData();
            ConnectionProvider connectionProvider = new ConnectionProvider();
            Connection connection = connectionProvider.getConnection();
            Statement stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet rs = stmt.executeQuery("SELECT * FROM SHOPS WHERE area = '" + postackData + "'");
            sendCarouselMessage(postbackEvent.getReplyToken(), rs);

        } else if (event instanceof BeaconEvent) {
            final BeaconEvent beaconEvent = (BeaconEvent) event;
            //reply(handleBeaconEvent(beaconEvent));
        }
    }

//    private void setUserProfile(String userId) throws Exception {
//        Response<UserProfileResponse> response =
//                LineMessagingServiceBuilder
//                        .create(System.getenv("LINE_BOT_CHANNEL_TOKEN"))
//                        .build()
//                        .getProfile(userId)
//                        .execute();
//        if (response.isSuccessful()) {
//            UserProfileResponse profile = response.body();
//            log.info(profile.getDisplayName());
//            log.info(profile.getPictureUrl());
//            log.info(profile.getStatusMessage());
//
//            HashMap<String, String> map = new HashMap<>();
//            map.put("displayName", profile.getDisplayName());
//            map.put("pictureUrl", profile.getPictureUrl());
//            jedis.hmset("userId:" + userId, map);
//
//        } else {
//            log.info(response.code() + " " + response.message());
//        }
//    }

    private void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        log.info("Text message event: " + event);
        log.info("SenderId: " + event.getSource().getSenderId());
        log.info("UserId: " + event.getSource().getUserId());

        // ユーザ情報取得
//        if (event.getSource().getUserId() != null) {
//            setUserProfile(event.getSource().getUserId());
//        }

        // area存在チェック
        String text = event.getMessage().getText();
        ConnectionProvider connectionProvider = new ConnectionProvider();
        Connection connection = connectionProvider.getConnection();
        Statement stmt = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet rs = stmt.executeQuery("SELECT * FROM SHOPS WHERE area = '" + text + "'");
        while (rs.next()) {
            log.info(rs.getString("title"));
            log.info(rs.getString("uri"));
            log.info(rs.getString("text"));
            log.info(rs.getString("thumbnailImageUrl"));
        }
        rs.last();
        int number_of_row = rs.getRow();
        if (number_of_row > 0) {
            // 指定された対象データ1件以上あり
            // ○○をご案内いたしましょうか？ Yes, No
            sendConfirmMessage(event.getReplyToken(), text);
        }
    }

    private void sendConfirmMessage(String replyToken, String area) throws Exception {

        List<Action> actions = new ArrayList<>();
        PostbackAction postbackAction = new PostbackAction(
                "Yes",
                area,
                "Yes");
        MessageAction messageAction = new MessageAction(
                "No",
                "No");

        actions.add(postbackAction);
        actions.add(messageAction);

        ConfirmTemplate confirmTemplate = new ConfirmTemplate(area + "のお店をご案内したします。よろしいですか？", actions);
        TemplateMessage templateMessage = new TemplateMessage(
                "this is a confirm template",
                confirmTemplate);

        ReplyMessage replyMessage = new ReplyMessage(
                replyToken,
                templateMessage
        );
        Response<BotApiResponse> response =
                LineMessagingServiceBuilder
                        .create(System.getenv("LINE_BOT_CHANNEL_TOKEN"))
                        .build()
                        .replyMessage(replyMessage)
                        .execute();
        log.info(response.code() + " " + response.message());
    }

    private void sendCarouselMessage(String replyToken, ResultSet rs) throws Exception {

        List<CarouselColumn> columns = new ArrayList<>();
        while (rs.next()) {
            ShopInfo shopInfo = new ShopInfo();
            shopInfo.setTitle(rs.getString("title"));
            shopInfo.setUri(rs.getString("uri"));
            shopInfo.setText(rs.getString("text"));
            shopInfo.setThumbnailImageUrl(rs.getString("thumbnailImageUrl"));
            CarouselColumn carouselColumn = createCarouselColumn(shopInfo);
            columns.add(carouselColumn);
        }

        CarouselTemplate carouselTemplate = new CarouselTemplate(columns);
        TemplateMessage templateMessage = new TemplateMessage(
                "this is a carousel template",
                carouselTemplate);

        ReplyMessage replyMessage = new ReplyMessage(
                replyToken,
                templateMessage
        );
        Response<BotApiResponse> response =
                LineMessagingServiceBuilder
                        .create(System.getenv("LINE_BOT_CHANNEL_TOKEN"))
                        .build()
                        .replyMessage(replyMessage)
                        .execute();
        log.info(response.code() + " " + response.message());
    }

    private CarouselColumn createCarouselColumn(ShopInfo shopInfo) throws Exception {

        List<Action> actions = new ArrayList<>();
        URIAction uriAction = new URIAction(
                "View detail",
                shopInfo.getUri());
        actions.add(uriAction);

        return new CarouselColumn(
                shopInfo.getThumbnailImageUrl(),
                shopInfo.getTitle(),
                shopInfo.getText(),
                actions);
    }

    private void sendMessage(String destination, String message) throws Exception {

        TextMessage textMessage = new TextMessage(message);
        PushMessage pushMessage = new PushMessage(
                destination,
                textMessage
        );
        Response<BotApiResponse> response =
                LineMessagingServiceBuilder
                        .create(System.getenv("LINE_BOT_CHANNEL_TOKEN"))
                        .build()
                        .pushMessage(pushMessage)
                        .execute();
        log.info(response.code() + " " + response.message());
    }

}
