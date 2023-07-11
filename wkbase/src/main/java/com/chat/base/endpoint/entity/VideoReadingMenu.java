package com.chat.base.endpoint.entity;

/**
 * 2020-11-06 15:13
 * 视频录制
 */
public class VideoReadingMenu extends BaseEndpoint {
    public IRedingResult iRedingResult;

    public VideoReadingMenu(IRedingResult iRedingResult) {
        this.iRedingResult = iRedingResult;
    }

    public interface IRedingResult {
        void onResult(long second, String path, String videoPath, long size);
    }
}
