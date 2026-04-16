package com.chat.uikit.category;

import java.util.List;

public class CategoryEntity {
    public String category_id;
    public String name;
    public int sort;
    public boolean is_default;
    public List<CategoryGroup> groups;

    public static class CategoryGroup {
        public String group_no;
        public String name;
        public int category_sort;
    }
}
