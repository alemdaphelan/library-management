import 'package:flutter/material.dart';

class BookCover extends StatelessWidget {
  final String? imageUrl;
  final String title;
  final String? author;
  final double width;
  final double height;
  final double borderRadius;

  const BookCover({
    super.key,
    this.imageUrl,
    required this.title,
    this.author,
    this.width = 70,
    this.height = 100,
    this.borderRadius = 8,
  });

  static const List<List<Color>> _gradients = [
    [Color(0xFF0f172a), Color(0xFF1e293b), Color(0xFF334155)], // Slate Navy
    [Color(0xFF064e3b), Color(0xFF047857), Color(0xFF059669)], // Emerald Forest
    [Color(0xFF1e1b4b), Color(0xFF312e81), Color(0xFF4338ca)], // Deep Indigo
    [Color(0xFF701a75), Color(0xFF86198f), Color(0xFFa21caf)], // Royal Purple
    [Color(0xFF7c2d12), Color(0xFF9a3412), Color(0xFFc2410c)], // Warm Auburn
    [Color(0xFF134e4a), Color(0xFF0f766e), Color(0xFF0d9488)], // Cyan Teal
  ];

  @override
  Widget build(BuildContext context) {
    String? secureUrl = imageUrl;
    if (secureUrl != null && secureUrl.startsWith('http://')) {
      secureUrl = secureUrl.replaceFirst('http://', 'https://');
    }

    if (secureUrl == null || secureUrl.trim().isEmpty) {
      return _buildFallbackCover();
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: Image.network(
        secureUrl,
        width: width,
        height: height,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) {
          return _buildFallbackCover();
        },
        loadingBuilder: (context, child, loadingProgress) {
          if (loadingProgress == null) return child;
          return Container(
            width: width,
            height: height,
            color: Colors.grey.shade200,
            child: const Center(
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildFallbackCover() {
    final paletteIndex = title.hashCode.abs() % _gradients.length;
    final gradient = _gradients[paletteIndex];

    final isSmall = width < 100;

    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(borderRadius),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: gradient,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.15),
            blurRadius: 6,
            offset: const Offset(2, 4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: Stack(
          children: [
            // Book spine imitation (left line)
            Positioned(
              left: 0,
              top: 0,
              bottom: 0,
              width: isSmall ? 4 : 8,
              child: Container(color: Colors.white.withValues(alpha: 0.12)),
            ),
            // Content
            Padding(
              padding: EdgeInsets.only(
                left: isSmall ? (width < 60 ? 6 : 8) : 16,
                right: isSmall ? (width < 60 ? 4 : 6) : 14,
                top: isSmall ? 6 : 14,
                bottom: isSmall ? 6 : 14,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Icon(
                        Icons.auto_stories_rounded,
                        size: isSmall ? 13 : 24,
                        color: Colors.amber.shade300,
                      ),
                      if (width >= 60)
                        Text(
                          'HUIT',
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.7),
                            fontSize: isSmall ? 8 : 11,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 0.8,
                          ),
                        ),
                    ],
                  ),
                  Expanded(
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2.0),
                        child: Text(
                          title,
                          textAlign: TextAlign.center,
                          maxLines: isSmall ? 3 : 5,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: isSmall ? 10 : 16,
                            fontWeight: FontWeight.bold,
                            height: 1.15,
                          ),
                        ),
                      ),
                    ),
                  ),
                  if (author != null && author!.isNotEmpty)
                    Text(
                      author!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.75),
                        fontSize: isSmall ? 9 : 12,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
