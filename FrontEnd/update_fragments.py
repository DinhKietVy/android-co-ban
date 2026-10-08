import os

def process_layout(file_path, title_string_res):
    if not os.path.exists(file_path):
        return
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    if '<LinearLayout xmlns:android' in content:
        print(f"Already processed {file_path}")
        return

    # Replace root tag
    content = content.replace('<androidx.constraintlayout.widget.ConstraintLayout', '<LinearLayout', 1)
    # Add orientation
    content = content.replace('android:layout_height="match_parent"\n', 'android:layout_height="match_parent"\n    android:orientation="vertical"\n', 1)
    
    # Extract the rest of the attributes
    parts = content.split('>', 1)
    header = parts[0] + '>'
    body = parts[1]
    
    # We remove the end tag of constraint layout
    body = body.rsplit('</androidx.constraintlayout.widget.ConstraintLayout>', 1)[0]
    
    new_header = f"""
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="16dp"
        android:paddingTop="30dp"
        android:paddingEnd="16dp"
        android:paddingBottom="12dp">

        <ImageButton
            android:id="@+id/headerMenuButton"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_marginEnd="16dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="Menu"
            android:src="@drawable/list"
            android:tint="@color/explorer_icon" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="{title_string_res}"
            android:textColor="@color/explorer_text_primary"
            android:textSize="22sp"
            android:textStyle="bold" />
    </LinearLayout>

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">
"""
    new_content = header + new_header + body + "\n    </androidx.constraintlayout.widget.ConstraintLayout>\n</LinearLayout>"
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f"Updated {file_path}")

def process_fragment(file_path):
    if not os.path.exists(file_path):
        return
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    if 'headerMenuButton' in content:
        print(f"Already processed {file_path}")
        return

    # Find the line after emptyStateContainer or recyclerView
    # We will just find 'val emptyStateContainer = view.findViewById' or similar and insert after it.
    insert_str = """
        view.findViewById<android.view.View>(R.id.headerMenuButton)?.setOnClickListener {
            (activity as? com.example.filemanagementapp.main.MainActivity)?.openDrawer()
        }
"""
    
    if 'val emptyStateContainer =' in content:
        parts = content.split('val emptyStateContainer =', 1)
        part2 = parts[1]
        line_end = part2.find('\n')
        new_content = parts[0] + 'val emptyStateContainer =' + part2[:line_end] + '\n' + insert_str + part2[line_end:]
    else:
        # Just put it right before return view
        parts = content.rsplit('return view', 1)
        new_content = parts[0] + insert_str + '        return view' + parts[1]

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f"Updated {file_path}")


base_path = r"c:\Users\Admin\StudioProjects\android-co-ban\FrontEnd\app\src\main"
layout_path = os.path.join(base_path, "res", "layout")
java_path = os.path.join(base_path, "java", "com", "example", "filemanagementapp")

process_layout(os.path.join(layout_path, "fragment_public_link.xml"), "@string/nav_public_links")
process_layout(os.path.join(layout_path, "fragment_recent.xml"), "@string/nav_recent")
process_layout(os.path.join(layout_path, "fragment_trash.xml"), "@string/nav_trash")

process_fragment(os.path.join(java_path, "publiclink", "PublicLinkFragment.kt"))
process_fragment(os.path.join(java_path, "recent", "RecentFragment.kt"))
process_fragment(os.path.join(java_path, "trash", "TrashFragment.kt"))
