package com.viger.plugin;

import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewsFeeder {
    public static final NewsFeeder INSTANCE = new NewsFeeder(); // 单例化
    Feed last; // 上一次更新内容
    private String timestamp; // 最后一篇已读文章的时间戳


    private NewsFeeder() {
        super();
        last = null;
        timestamp = "0";
    }


    /**
     * 检查是否有未读文章
     *
     * @return 是否有未读文章
     */
    boolean unread() throws IOException {
        Document doc;
        Feed feed = null;
        Element root;
        try {
            String url = "https://rsshub.viger.xyz/bilibili/user/dynamic/353840826";
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(url);
            String title, link, description, language, guid, pubdate;
            root = ((Element) ((Element) doc.getElementsByTagName("rss").item(0))
                    .getElementsByTagName("channel").item(0));
            title = root.getElementsByTagName("title").item(0).getTextContent();
            link = root.getElementsByTagName("link").item(0).getTextContent();
            language = root.getElementsByTagName("language").item(0).getTextContent();
            description = root.getElementsByTagName("description").item(0).getTextContent();
            feed = new Feed(title, link, description, language); // rss解析成feed类


            NodeList feedsList = root.getElementsByTagName("item");
            Element feedElement;

            for (int i = 0; i < feedsList.getLength(); i++) {
                feedElement = ((Element) feedsList.item(i));
                guid = feedElement.getElementsByTagName("guid").item(0).getTextContent();
                guid = guid.replace("https://t.bilibili.com/", "");
                title = feedElement.getElementsByTagName("title").item(0).getTextContent();
                description = feedElement.getElementsByTagName("description").item(0).getTextContent();
                link = feedElement.getElementsByTagName("link").item(0).getTextContent();
                pubdate = feedElement.getElementsByTagName("pubDate").item(0).getTextContent();
                feed.getMessages().add(new FeedMessage(title, link, description, guid, pubdate));
            } // 填充rss消息

        } catch (SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        if (last == null) {
            if (feed != null) {
                feed.getMessages().removeAll(feed.getMessages().subList(1, feed.getMessages().size()));
                System.out.print(feed.getMessages().size() + "\n无更新内容");
                last = feed;
                timestamp = last.getMessages().get(0).getGuid();
            }
            return false;
        } else if (feed != null && feed.getMessages().get(0).guid.equals(timestamp)) {
            System.out.print(feed.getMessages().get(0) + "\n无更新内容");
            return false;
        } else {
            last = feed;
            System.out.print(feed.getMessages().get(0) + "\n检查到更新");
            return true;
        } // 检查是否有更新
    }

    /**
     * @param contact 要发送的对象
     * @return 最近一条文章
     * @throws MalformedURLException .
     */
    public Message last(Contact contact) throws MalformedURLException {
        MessageChainBuilder message;
        FeedMessage feed = last.getMessages().get(0);
        message = getSingleMessages(contact, feed);
        timestamp = last.getMessages().get(0).getGuid();
        return message.asMessageChain();
    }

    /**
     * 获取最新的文章们
     *
     * @param contact 要发送的对象
     * @return 未读文章消息化
     */
    LinkedList<Message> fetch(Contact contact) throws MalformedURLException {
        LinkedList<Message> result = new LinkedList<>();
        MessageChainBuilder message;
        LinkedList<Image> img;
        String text;
        for (FeedMessage feed : last.getMessages()) {
            if (Long.parseLong(feed.getGuid()) <= Long.parseLong(timestamp)) {
                break;
            } else {
                message = getSingleMessages(contact, feed);
                result.add(message.asMessageChain());
            }
        }
        timestamp = last.getMessages().get(0).getGuid();
        return result;
    }

    /**
     * @param contact 要发送的对象
     * @param feed    单条文章
     * @return 消息化文章
     * @throws MalformedURLException .
     */
    @NotNull
    private MessageChainBuilder getSingleMessages(Contact contact, FeedMessage feed) throws MalformedURLException {
        MessageChainBuilder message;
        String text;
        LinkedList<Image> img;
        message = new MessageChainBuilder();
        message.add("国服动态更新:\n");
        text = feed.getDescription();
        text.replaceAll("<img.*?src=\"(.*?)\".*?>", "&flagImg");
        img = getImage(feed.getDescription(), contact);
        text = text.replaceAll("<br>", "\n");
        text = text.replaceAll("<.*>", "");
        message.add(text);
        message.addAll(img);
        message.add(feed.getLink());
        return message;
    }

    /**
     * @param description 动态的html文本
     * @return 动态中的图片
     */
    private LinkedList<Image> getImage(String description, Contact contact) throws MalformedURLException {
        LinkedList<Image> img = new LinkedList<>();
        Pattern IMG_PATTERN = Pattern.compile(
                "<img.*?src=\"(.*?)\".*?>",
                Pattern.CASE_INSENSITIVE);
        Matcher IMG_MATCHER = IMG_PATTERN.matcher(description);
        while (IMG_MATCHER.find()) {
            img.add(contact.uploadImage(new URL(IMG_MATCHER.group(1))));
        }
        return img;
    }
}


