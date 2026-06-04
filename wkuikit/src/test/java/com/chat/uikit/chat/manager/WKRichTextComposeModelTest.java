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

package com.chat.uikit.chat.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.chat.uikit.chat.manager.WKRichTextComposeModel.WKRichTextContentBlocks;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Locks the RichText (ContentType=14) <em>input-box attachment tray</em> ordering model
 * (Phase 2, octo-web#237). The tray is the Android answer to "true interleave": images
 * accumulate as reorderable thumbnails, text stays a live draft, and on send the model
 * yields [text block] + [ordered image blocks] for a single type=14 message.
 *
 * <p>These tests pin the pure data-model behaviour — add / remove-by-id / reorder /
 * ordered-paths / preview-blocks — under plain JVM (no Android framework), mirroring the
 * Phase 1 decision to extract the testable core out of the View layer.
 */
public class WKRichTextComposeModelTest {

    @Test
    public void newModel_isEmpty() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        assertTrue(model.isEmpty());
        assertEquals(0, model.size());
        assertTrue(model.orderedPaths().isEmpty());
    }

    @Test
    public void addAll_appendsInOrder_skippingBlankPaths() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        int added = model.addAll(Arrays.asList("/a.png", "", null, "/b.png"));
        assertEquals(2, added);
        assertEquals(Arrays.asList("/a.png", "/b.png"), model.orderedPaths());
    }

    @Test
    public void addAll_multipleBatches_keepCumulativeOrder() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png"));
        model.addAll(Arrays.asList("/c.png"));
        assertEquals(Arrays.asList("/a.png", "/b.png", "/c.png"), model.orderedPaths());
    }

    @Test
    public void removeById_removesExactItem_byStableId_notIndex() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png", "/c.png"));
        long middleId = model.items().get(1).id;
        assertTrue(model.removeById(middleId));
        assertEquals(Arrays.asList("/a.png", "/c.png"), model.orderedPaths());
    }

    @Test
    public void removeById_unknownId_isNoOp() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png"));
        assertFalse(model.removeById(999999L));
        assertEquals(1, model.size());
    }

    @Test
    public void move_reordersItems_reflectingRealOrder() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png", "/c.png"));
        // adjacent move: 0 -> 1
        assertTrue(model.move(0, 1));
        assertEquals(Arrays.asList("/b.png", "/a.png", "/c.png"), model.orderedPaths());
    }

    @Test
    public void move_nonAdjacent_isTrueMove_notSwap() {
        // Regression: a drag that skips slots must remove+insert (true move), not swap
        // endpoints. Dragging A from 0 to 2 must yield [B, C, A], NOT the swap result
        // [C, B, A] — otherwise the sent payload order diverges from what the user sees.
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png", "/c.png"));
        assertTrue(model.move(0, 2));
        assertEquals(Arrays.asList("/b.png", "/c.png", "/a.png"), model.orderedPaths());
    }

    @Test
    public void move_backwards_nonAdjacent_isTrueMove() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png", "/c.png", "/d.png"));
        // drag D from 3 to 0 -> [D, A, B, C]
        assertTrue(model.move(3, 0));
        assertEquals(Arrays.asList("/d.png", "/a.png", "/b.png", "/c.png"), model.orderedPaths());
    }

    @Test
    public void move_outOfBoundsOrSameIndex_isNoOp() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png"));
        assertFalse(model.move(0, 0));
        assertFalse(model.move(-1, 1));
        assertFalse(model.move(0, 5));
        assertEquals(Arrays.asList("/a.png", "/b.png"), model.orderedPaths());
    }

    @Test
    public void clear_emptiesTray() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png"));
        model.clear();
        assertTrue(model.isEmpty());
        assertTrue(model.orderedPaths().isEmpty());
    }

    @Test
    public void items_returnsDefensiveCopy_callerMutationDoesNotAffectModel() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png"));
        model.items().clear();
        assertEquals(2, model.size());
    }

    @Test
    public void previewBlocks_textFirst_thenImagesInTrayOrder() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png"));
        List<WKRichTextContentBlocks> blocks = model.previewBlocks("hello");
        assertEquals(3, blocks.size());
        assertTrue(blocks.get(0).isText());
        assertEquals("hello", blocks.get(0).text);
        assertTrue(blocks.get(1).isImage());
        assertEquals("/a.png", blocks.get(1).localPath);
        assertTrue(blocks.get(2).isImage());
        assertEquals("/b.png", blocks.get(2).localPath);
    }

    @Test
    public void previewBlocks_blankText_omitsTextBlock_pureImageTray() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png"));
        List<WKRichTextContentBlocks> blocks = model.previewBlocks("   ");
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).isImage());
    }

    @Test
    public void previewBlocks_reflectsReorder() {
        WKRichTextComposeModel model = new WKRichTextComposeModel();
        model.addAll(Arrays.asList("/a.png", "/b.png"));
        model.move(0, 1);
        List<WKRichTextContentBlocks> blocks = model.previewBlocks("");
        assertEquals("/b.png", blocks.get(0).localPath);
        assertEquals("/a.png", blocks.get(1).localPath);
    }
}
