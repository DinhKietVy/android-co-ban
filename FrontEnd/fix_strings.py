import os
import codecs

path = r"c:\Users\Admin\StudioProjects\android-co-ban\FrontEnd\app\src\main\res\values\strings.xml"
with codecs.open(path, 'r', 'utf-8', errors='ignore') as f:
    content = f.read()

# find </resources> and replace everything from there to the end
idx = content.find('</resources>')
if idx != -1:
    content = content[:idx] + """    <string name="nav_shared">Được chia sẻ với tôi</string>
    <string name="nav_public_links">Liên kết công khai</string>
</resources>
"""

with codecs.open(path, 'w', 'utf-8') as f:
    f.write(content)
print("strings.xml fixed")
