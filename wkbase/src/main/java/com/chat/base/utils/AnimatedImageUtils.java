/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class AnimatedImageUtils {

    public static final long MAX_ANIMATED_AVATAR_BYTES = 5L * 1024 * 1024;

    public static boolean isAnimatedGif(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[6];
            if (fis.read(header) < 6) return false;
            // GIF87a or GIF89a
            return header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9')
                    && header[5] == 'a';
        } catch (IOException e) {
            return false;
        }
    }
}
