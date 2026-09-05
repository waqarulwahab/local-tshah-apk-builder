import 'package:flutter/material.dart';

class AIInfoScreen extends StatelessWidget {
  const AIInfoScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 200,
            floating: true,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              title: const Text('AI Assistant'),
              background: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Color(0xFF1A237E),
                      Color(0xFF1565C0),
                      Color(0xFF00695C),
                    ],
                  ),
                ),
                child: Center(
                  child: Icon(
                    Icons.auto_awesome,
                    size: 80,
                    color: Colors.white,
                  ),
                ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildSection(
                    title: 'What is AI Assistant?',
                    content:
                        'AI Assistant is an intelligent digital helper that can answer your questions. Using advanced artificial intelligence technology, it can communicate with you naturally and provide detailed information on any topic.',
                  ),
                  SizedBox(height: 20),
                  _buildSection(
                    title: 'Key Features',
                    content:
                        '• Instant Answers - Get quick responses to any question\n• Multi-language Support - English, Urdu, Hindi and more\n• Smart Suggestions - Detailed information on every topic\n• Secure Chat - Your privacy is completely protected\n• 24/7 Available - Accessible anytime you need help',
                  ),
                  SizedBox(height: 20),
                  _buildSection(
                    title: 'How to Use?',
                    content:
                        '1. Type your question or topic\n2. Send your message to AI Assistant\n3. Get instant answer and ask follow-up questions\n4. Receive short or detailed answers on different topics\n5. Learn and explore at your own pace',
                  ),
                  SizedBox(height: 20),
                  _buildSection(
                    title: 'Benefits',
                    content:
                        'Saves your time by providing instant answers. Helps with learning and skill development. Assists in problem-solving. Improves knowledge and understanding. Works offline and online. No subscription fees required.',
                  ),
                  SizedBox(height: 20),
                  _buildSection(
                    title: 'Advanced AI Technology',
                    content:
                        'AI Assistant uses advanced machine learning and natural language processing. It learns from millions of texts to understand your questions and provide accurate, detailed responses. Continuously improves from every interaction.',
                  ),
                  SizedBox(height: 20),
                  _buildSection(
                    title: 'Privacy & Security',
                    content:
                        'All your information is secure and encrypted. We never share your personal data with anyone. You have complete control over your data. Your conversations are private and protected with enterprise-level security.',
                  ),
                  SizedBox(height: 30),
                  Container(
                    padding: EdgeInsets.all(15),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Color(0xFF1A237E).withOpacity(0.1),
                          Color(0xFF1565C0).withOpacity(0.1),
                        ],
                      ),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: Color(0xFF1565C0),
                        width: 1,
                      ),
                    ),
                    child: Column(
                      children: [
                        Icon(
                          Icons.lightbulb,
                          color: Color(0xFF1565C0),
                          size: 40,
                        ),
                        SizedBox(height: 10),
                        Text(
                          'Keep Learning & Growing',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFF1565C0),
                          ),
                        ),
                        SizedBox(height: 8),
                        Text(
                          'Learn new information from AI Assistant. Improve your skills. Discover something new every day.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.grey[700],
                          ),
                        ),
                      ],
                    ),
                  ),
                  SizedBox(height: 30),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSection({required String title, required String content}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: Color(0xFF1565C0),
          ),
        ),
        SizedBox(height: 10),
        Text(
          content,
          style: TextStyle(
            fontSize: 14,
            color: Colors.grey[700],
            height: 1.6,
          ),
        ),
      ],
    );
  }
}
