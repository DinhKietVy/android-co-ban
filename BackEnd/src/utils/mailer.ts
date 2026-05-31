import nodemailer from 'nodemailer';

const smtpPort = parseInt(process.env.SMTP_PORT || '465', 10);

const transporter = nodemailer.createTransport({
  host: process.env.SMTP_HOST || 'smtp.gmail.com',
  port: smtpPort,
  secure: smtpPort === 465,
  auth: {
    user: process.env.SMTP_USER || '', // Tên đăng nhập email của bạn
    pass: process.env.SMTP_PASS || '', // Mật khẩu ứng dụng (App Password)
  },
});

export const sendResetPasswordEmail = async (to: string, resetCode: string) => {
  const mailOptions = {
    from: `"Support Team" <${process.env.SMTP_USER || 'no-reply@example.com'}>`,
    to,
    subject: 'Mã đặt lại mật khẩu của bạn',
    html: `
      <div style="font-family: Arial, sans-serif; padding: 20px;">
        <h2>Đặt lại mật khẩu</h2>
        <p>Bạn đã yêu cầu đặt lại mật khẩu. Vui lòng sử dụng mã bên dưới để đổi mật khẩu mới. Mã này sẽ hết hạn trong 15 phút.</p>
        <h1 style="color: #4CAF50; font-size: 32px; letter-spacing: 5px;">${resetCode}</h1>
        <p>Nếu bạn không yêu cầu, vui lòng bỏ qua email này.</p>
      </div>
    `,
  };

  try {
    await transporter.sendMail(mailOptions);
    console.log(`Reset password email sent to ${to}`);
  } catch (error) {
    console.error('Error sending email:', error);
    throw new Error('Khong the gui email');
  }
};
