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

export const sendNewDeviceOTPEmail = async (
  to: string,
  otpCode: string,
  deviceName: string,
  ipAddress: string
) => {
  const mailOptions = {
    from: `"Support Team" <${process.env.SMTP_USER || 'no-reply@example.com'}>`,
    to,
    subject: 'Xác minh đăng nhập từ thiết bị mới',
    html: `
      <div style="font-family: Arial, sans-serif; padding: 20px; max-width: 500px;">
        <h2 style="color: #333;">Phát hiện đăng nhập từ thiết bị mới</h2>
        <p>Chúng tôi phát hiện có lần đăng nhập vào tài khoản của bạn từ một thiết bị chưa được tin tưởng.</p>
        <table style="border-collapse: collapse; width: 100%; margin: 16px 0;">
          <tr>
            <td style="padding: 8px; font-weight: bold; color: #555;">Thiết bị:</td>
            <td style="padding: 8px; color: #333;">${deviceName}</td>
          </tr>
          <tr style="background: #f9f9f9;">
            <td style="padding: 8px; font-weight: bold; color: #555;">Địa chỉ IP:</td>
            <td style="padding: 8px; color: #333;">${ipAddress}</td>
          </tr>
          <tr>
            <td style="padding: 8px; font-weight: bold; color: #555;">Thời gian:</td>
            <td style="padding: 8px; color: #333;">${new Date().toLocaleString('vi-VN')}</td>
          </tr>
        </table>
        <p>Nhập mã xác minh bên dưới để hoàn tất đăng nhập. Mã có hiệu lực trong <strong>10 phút</strong>.</p>
        <div style="text-align: center; margin: 24px 0;">
          <span style="display: inline-block; background: #f0f4ff; border: 2px dashed #4a6cf7; border-radius: 8px; padding: 16px 32px; font-size: 36px; font-weight: bold; color: #4a6cf7; letter-spacing: 8px;">${otpCode}</span>
        </div>
        <p style="color: #888; font-size: 13px;">Nếu bạn không thực hiện đăng nhập này, hãy đổi mật khẩu ngay lập tức.</p>
      </div>
    `,
  };

  try {
    await transporter.sendMail(mailOptions);
    console.log(`New device OTP email sent to ${to}`);
  } catch (error) {
    console.error('Error sending new device OTP email:', error);
    throw new Error('Khong the gui email xac minh thiet bi');
  }
};

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
