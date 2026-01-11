package com.example.pra.ui.components.player

import com.example.pra.ui.components.common.FrostedGlassButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha // 关键引用
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pra.R

@Composable
fun TopBarSection(
    modifier: Modifier = Modifier,
    // 新增参数：控制是否可见
    isVisible: Boolean = false, // 默认为 false，即“占位但不显示”
    onMoreClick: () -> Unit = {},
    onMinimizeClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // --- 核心修改 ---
            // 如果 isVisible 为 true，透明度为 1 (显示)
            // 如果 isVisible 为 false，透明度为 0 (隐藏但占位)
            .alpha(if (isVisible) 1f else 0f)
    ) {
        // ... 内部代码完全不需要动 ...

        // 1. 状态行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "15:00",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "12月12日 周五",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. 控制行
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            FrostedGlassButton(
                painter = painterResource(id = R.drawable.info_solid),
                contentDescription = "More",
                iconTint = Color.White.copy(alpha = 0.9f),
                onClick = {
                    // 如果隐藏时不希望被点击，可以在这里加判断
                    if (isVisible) onMoreClick()
                },
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterStart)
            )

            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.4f))
                    .clickable {
                        if (isVisible) onMinimizeClick()
                    }
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterEnd)
            )
        }
    }
}
